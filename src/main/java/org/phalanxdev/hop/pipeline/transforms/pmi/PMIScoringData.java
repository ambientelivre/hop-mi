/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phalanxdev.hop.pipeline.transforms.pmi;

import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.core.row.IRowMeta;
import org.phalanxdev.hop.utils.LogAdapter;
import org.phalanxdev.hop.utils.BaseMessagesAdapter;
import org.phalanxdev.hop.utils.VariablesAdapter;
import org.phalanxdev.mi.Evaluator;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.Utils;
import weka.core.pmml.PMMLFactory;
import weka.core.pmml.PMMLModel;
import weka.core.xml.XStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Data class for the PMI scoring step.
 *
 * @author Mark Hall (mhall{[at]}waikato{[dot]}ac{[dot]}nz)
 * @version $Revision: $
 */
public class PMIScoringData extends BaseTransformData implements ITransformData {

  /**
   * some constants for various input field - attribute match/type problems
   */
  public static final int NO_MATCH = -1;
  public static final int TYPE_MISMATCH = -2;

  /**
   * the output data format
   */
  protected IRowMeta m_outputRowMeta;

  /**
   * holds values for instances constructed for prediction
   */
  private double[] m_vals = null;

  /**
   * Holds the actual PMI model (classifier, clusterer or PMML) used by this copy of the step
   */
  protected transient PMIScoringModel m_model;

  /**
   * Holds a default model - only used when model files are sourced from a field in the incoming
   * data rows. In this case, it is the fallback model if there is no model file specified in the
   * incoming row.
   */
  protected PMIScoringModel m_defaultModel;

  /**
   * used to map attribute indices to incoming field indices
   */
  private int[] m_mappingIndexes;

  /**
   * whether to update the model (if incremental)
   */
  protected boolean m_updateIncrementalModel;

  /**
   * Whether to perform supervised evaluation on incoming data (assuming targets are present),
   * rather than outputting scored rows.
   */
  protected boolean m_performEvaluation;

  /**
   * True if information retrieval metrics are to be output when evaluating
   */
  protected boolean m_outputIRMetrics;

  /**
   * True if area under the curve metrics are to be output (these require caching predictions, so
   * will not be suitable for very large test sets)
   */
  protected boolean m_outputAUCMetrics;

  /**
   * Accumulates evaluation statistics
   */
  protected Evaluator m_eval;

  /**
   * Set the model for this copy of the step to use
   *
   * @param model the model to use
   */
  public void setModel(PMIScoringModel model) {
    m_model = model;
    if (model != null && m_eval != null && model.isSupervisedLearningModel()) {
      m_eval.setTrainedClassifier((Classifier) model.getModel());
    }
  }

  /**
   * Get the model that this copy of the step is using
   *
   * @return the model that this copy of the step is using
   */
  public PMIScoringModel getModel() {
    return m_model;
  }

  /**
   * Set the default model for this copy of the step to use. This gets used if we are getting model
   * file paths from a field in the incoming row structure and a given row has null for the model
   * path.
   *
   * @param model the model to use as fallback
   */
  public void setDefaultModel(PMIScoringModel model) {
    m_defaultModel = model;
  }

  /**
   * Get the default model for this copy of the step to use. This gets used if we are getting model
   * file paths from a field in the incoming row structure and a given row has null for the model
   * path.
   *
   * @return the model to use as fallback
   */
  public PMIScoringModel getDefaultModel() {
    return m_defaultModel;
  }

  /**
   * Get the meta data for the output format
   *
   * @return a <code>IRowMeta</code> value
   */
  public IRowMeta getOutputRowMeta() {
    return m_outputRowMeta;
  }

  /**
   * Set the meta data for the output format
   *
   * @param rmi a <code>IRowMeta</code> value
   */
  public void setOutputRowMeta(IRowMeta rmi) {
    m_outputRowMeta = rmi;
  }

  /**
   * Initialize the evaluation object (if necessary)
   *
   * @param scoringMeta the metadata for the scoring step
   */
  public void initEvaluation(PMIScoringMeta scoringMeta) throws Exception {
    m_eval =
        new Evaluator(Evaluator.EvalMode.SEPARATE_TEST_SET, 1, scoringMeta.getOutputAUCMetrics(),
            scoringMeta.getOutputIRMetrics(), new BaseMessagesAdapter(BaseSupervisedPMIMeta.class));
    m_eval.initialize(m_model.getHeader(), (Classifier) m_model.getModel());
    if (m_model.isSupervisedLearningModel()
        && ((PMIScoringClassifier) m_model).getEvaluation() != null) {
      m_eval.setEvaluation(((PMIScoringClassifier) m_model).getEvaluation());
    }
  }

  /**
   * Checks whether the class attribute is present in the incoming row metadata and whether it is of
   * the correct type. Assumes that mapIncomingRowMetaData() has already been called. Note that
   * until rows are seen, it is not known whether non-missing class values are actually present in
   * the data.
   *
   * @param header the Instances header used when the model was trained.
   * @return -1 if class missing, -2 if type mismatch and 0 if OK
   * @throws if the header does not have a class set
   */
  public int checkClassForEval(Instances header) throws Exception {
    if (header.classIndex() < 0) {
      throw new Exception("No class attribute set in the model header!");
    }

    return m_mappingIndexes[header.classIndex()];
  }

  /**
   * Finds a mapping between the attributes that a Weka model has been trained with and the incoming
   * Kettle row format. Returns an array of indices, where the element at index 0 of the array is
   * the index of the Kettle field that corresponds to the first attribute in the Instances
   * structure, the element at index 1 is the index of the Kettle fields that corresponds to the
   * second attribute, ...
   *
   * @param header the Instances header
   * @param inputRowMeta the meta data for the incoming rows
   * @param updateIncrementalModel true if the model is incremental and should be updated on the
   * incoming instances
   * @param log the log to use
   */
  public void mapIncomingRowMetaData(Instances header, IRowMeta inputRowMeta,
      boolean updateIncrementalModel,
      ILogChannel log) {
    m_mappingIndexes = PMIScoringData.findMappings(header, inputRowMeta);
    m_updateIncrementalModel = updateIncrementalModel;

    // If updating of incremental models has been selected, then
    // check on the ability to do this
    if (m_updateIncrementalModel && m_model.isSupervisedLearningModel()) {
      if (m_model.isUpdateableModel()) {
        // Do we have the class mapped successfully to an incoming
        // Kettle field
        if (m_mappingIndexes[header.classIndex()] == PMIScoringData.NO_MATCH
            || m_mappingIndexes[header.classIndex()] == PMIScoringData.TYPE_MISMATCH) {
          m_updateIncrementalModel = false;
          log.logError(
              BaseMessages.getString(PMIScoringMeta.PKG, "PMIScoringMeta.Log.NoMatchForClass"));
        }
      } else {
        m_updateIncrementalModel = false;
        log.logError(
            BaseMessages.getString(PMIScoringMeta.PKG, "PMIScoringMeta.Log.ModelNotUpdateable"));
      }
    }
  }

  /**
   * Loads a serialized model. Models can either be binary serialized Java objects, objects
   * deep-serialized to xml, or PMML.
   *
   * @param modelFile a <code>File</code> value
   * @return the model
   * @throws Exception if there is a problem laoding the model.
   */
  public static PMIScoringModel loadSerializedModel(String modelFile, ILogChannel log,
      IVariables space)
      throws Exception {

    Object model = null;
    Instances header = null;
    Evaluation classPriorEval = null;
    int[] ignoredAttsForClustering = null;

    modelFile = space.environmentSubstitute(modelFile);
    FileObject modelF = HopVfs.getFileObject(modelFile);
    if (!modelF.exists()) {
      throw new Exception(
          BaseMessages.getString(PMIScoringMeta.PKG, "PMIScoring.Error.NonExistentModelFile",
              space.environmentSubstitute(modelFile)));
    }

    InputStream is = HopVfs.getInputStream(modelF);
    BufferedInputStream buff = new BufferedInputStream(is);

    if (modelFile.toLowerCase().endsWith(".xml")) {
      // assume it is PMML
      model = PMMLFactory.getPMMLModel(buff, null);

      // we will use the mining schema as the instance structure
      header = ((PMMLModel) model).getMiningSchema().getMiningSchemaAsInstances();

      buff.close();
    } else if (modelFile.toLowerCase().endsWith(".xstreammodel")) {
      log.logBasic(BaseMessages.getString(PMIScoringMeta.PKG, "PMIScoringData.Log.LoadXMLModel"));

      if (XStream.isPresent()) {
        Vector v = (Vector) XStream.read(buff);

        model = v.elementAt(0);
        if (v.size() == 2) {
          // try and grab the header
          header = (Instances) v.elementAt(1);
        }
        buff.close();
      } else {
        buff.close();
        throw new Exception(
            BaseMessages.getString(PMIScoringMeta.PKG, "PMIScoringData.Error.CantLoadXMLModel"));
      }
    } else {
      InputStream stream = buff;
      if (modelFile.toLowerCase().endsWith(".gz")) {
        stream = new GZIPInputStream(buff);
      }
      ObjectInputStream oi = SerializationHelper.getObjectInputStream(stream);

      model = oi.readObject();

      // try and grab the header
      header = (Instances) oi.readObject();

      // try and grab an Eval object for training data class priors
      if (model instanceof Classifier) {
        try {
          classPriorEval = (Evaluation) oi.readObject();
        } catch (Exception ex) {
          // ignore
        }
      }

      if (model instanceof weka.clusterers.Clusterer) {
        // try and grab any attributes to be ignored during clustering
        try {
          ignoredAttsForClustering = (int[]) oi.readObject();
        } catch (Exception ex) {
          // Don't moan if there aren't any :-)
        }
      }
      oi.close();
    }

    Evaluator.configureWekaEnvironmentHandler(model, new VariablesAdapter(space));

    PMIScoringModel
        wsm =
        classPriorEval == null ? PMIScoringModel.createScorer(model) :
            PMIScoringModel.createScorer(model, classPriorEval);
    wsm.setHeader(header);
    if (wsm instanceof PMIScoringClusterer && ignoredAttsForClustering != null) {
      ((PMIScoringClusterer) wsm).setAttributesToIgnore(ignoredAttsForClustering);
    }

    wsm.setLog(log);
    return wsm;
  }

  public static void saveSerializedModel(PMIScoringModel wsm, File saveTo) throws Exception {

    Object model = wsm.getModel();
    Instances header = wsm.getHeader();
    header =
        header
            .stringFreeStructure(); // make sure we don't serialize any string/relational values into the model file
    OutputStream os = new FileOutputStream(saveTo);

    if (saveTo.getName().toLowerCase().endsWith(".gz")) { //$NON-NLS-1$
      os = new GZIPOutputStream(os);
    }
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os));

    oos.writeObject(model);
    oos.writeObject(header);
    oos.close();
  }

  /**
   * Finds a mapping between the attributes that a PMI model has been trained with and the incoming
   * Kettle row format. Returns an array of indices, where the element at index 0 of the array is
   * the index of the Kettle field that corresponds to the first attribute in the Instances
   * structure, the element at index 1 is the index of the Kettle fields that corresponds to the
   * second attribute, ...
   *
   * @param header the Instances header
   * @param inputRowMeta the meta data for the incoming rows
   * @return the mapping as an array of integer indices
   */
  public static int[] findMappings(Instances header, IRowMeta inputRowMeta) {
    // Instances header = m_model.getHeader();
    int[] mappingIndexes = new int[header.numAttributes()];

    Map<String, Integer> inputFieldLookup = new HashMap<String, Integer>();
    for (int i = 0; i < inputRowMeta.size(); i++) {
      IValueMeta inField = inputRowMeta.getValueMeta(i);
      inputFieldLookup.put(inField.getName(), i);
    }

    // check each attribute in the header against what is incoming
    for (int i = 0; i < header.numAttributes(); i++) {
      Attribute temp = header.attribute(i);
      String attName = temp.name();

      // look for a matching name
      Integer matchIndex = inputFieldLookup.get(attName);
      boolean ok = false;
      int status = NO_MATCH;
      if (matchIndex != null) {
        // check for type compatibility
        IValueMeta tempField = inputRowMeta.getValueMeta(matchIndex);
        if (tempField.isNumeric() || tempField.isBoolean()) {
          if (temp.isNumeric()) {
            ok = true;
            status = 0;
          } else {
            status = TYPE_MISMATCH;
          }
        } else if (tempField.isString()) {
          if (temp.isNominal() || temp.isString()) {
            ok = true;
            status = 0;
            // All we can assume is that this input field is ok.
            // Since we wont know what the possible values are
            // until the data is pumping throug, we will defer
            // the matching of legal values until then
          } else {
            status = TYPE_MISMATCH;
          }
        } else {
          // any other type is a mismatch (might be able to do
          // something with dates at some stage)
          status = TYPE_MISMATCH;
        }
      }
      if (ok) {
        mappingIndexes[i] = matchIndex;
      } else {
        // mark this attribute as missing or type mismatch
        mappingIndexes[i] = status;
      }
    }
    return mappingIndexes;
  }

  /**
   * Generates a batch of predictions (more specifically, an array of output rows containing all
   * input Kettle fields plus new fields that hold the prediction(s)) for each incoming Kettle row
   * given a PMI model.
   *
   * @param inputMeta the meta data for the incoming rows
   * @param outputMeta the meta data for the output rows
   * @param inputRows the values of the incoming row
   * @param meta meta data for this step
   * @return a Kettle row containing all incoming fields along with new ones that hold the
   * prediction(s)
   * @throws Exception if an error occurs
   */
  public Object[][] generatePredictions(IRowMeta inputMeta, IRowMeta outputMeta,
      List<Object[]> inputRows, PMIScoringMeta meta) throws Exception {

    int[] mappingIndexes = m_mappingIndexes;
    PMIScoringModel model = getModel(); // copy of the model for this copy of
    // the step
    boolean outputProbs = meta.getOutputProbabilities();
    boolean supervised = model.isSupervisedLearningModel();

    Attribute classAtt = null;
    if (supervised) {
      classAtt = model.getHeader().classAttribute();
    }

    Instances batch = new Instances(model.getHeader(), inputRows.size());
    for (Object[] r : inputRows) {
      Instance inst = constructInstance(batch, inputMeta, r, mappingIndexes, model, true, true);
      batch.add(inst);
    }
    for (int i = 0; i < batch.numInstances(); i++) {
      batch.instance(i).setClassMissing();
    }

    double[][] preds = model.distributionsForInstances(batch);

    Object[][] result = new Object[preds.length][];
    for (int i = 0; i < preds.length; i++) {
      // First copy the input data to the new result...
      Object[] resultRow = RowDataUtil.resizeArray(inputRows.get(i), outputMeta.size());
      int index = inputMeta.size();

      double[] prediction = preds[i];

      if (supervised) {
        if (classAtt.isNumeric()) {
          resultRow[index++] = prediction[0];
        } else {
          int maxProb = Utils.maxIndex(prediction);
          if (prediction[maxProb] > 0) {
            resultRow[index++] = classAtt.value(maxProb);
          } else {
            resultRow[index++] = BaseMessages
                .getString(PMIScoringMeta.PKG, "PMIScoringData.Message.UnableToPredict");
          }
        }
      } else {
        int maxProb = Utils.maxIndex(prediction);
        if (prediction[maxProb] > 0) {
          resultRow[index++] = maxProb;
        } else {
          resultRow[index++] =
              BaseMessages
                  .getString(PMIScoringMeta.PKG, "PMIScoringData.Message.UnableToPredictCluster");
        }
      }

      if ((outputProbs && classAtt == null) || (outputProbs && !classAtt.isNumeric())) {
        // output probability distribution
        for (double j : prediction) {
          resultRow[index++] = j;
        }
        int maxProb = Utils.maxIndex(prediction);
        resultRow[index] = prediction[maxProb];
      }

      result[i] = resultRow;
    }

    return result;
  }

  /**
   * Generates a prediction (more specifically, an output row containing all input Kettle fields
   * plus new fields that hold the prediction(s)) for an incoming Kettle row given a Weka model.
   *
   * @param inputMeta the meta data for the incoming rows
   * @param outputMeta the meta data for the output rows
   * @param inputRow the values of the incoming row
   * @param meta meta data for this step
   * @return a Kettle row containing all incoming fields along with new ones that hold the
   * prediction(s)
   * @throws Exception if an error occurs
   */
  public Object[] generatePrediction(IRowMeta inputMeta, IRowMeta outputMeta, Object[] inputRow,
      PMIScoringMeta meta) throws Exception {

    int[] mappingIndexes = m_mappingIndexes;
    PMIScoringModel model = getModel();
    boolean outputProbs = meta.getOutputProbabilities();
    boolean supervised = model.isSupervisedLearningModel();

    Attribute classAtt = null;
    if (supervised) {
      classAtt = model.getHeader().classAttribute();
    }

    // need to construct an Instance to represent this
    // input row
    Instance toScore = constructInstance(model.getHeader(), inputMeta, inputRow, mappingIndexes,
        model, false, false);
    if (supervised) {
      toScore.setClassMissing();
    }
    double[] prediction = model.distributionForInstance(toScore);

    // Update the model??
    if (meta.getUpdateIncrementalModel() && model.isUpdateableModel() && !toScore
        .isMissing(toScore.classIndex())) {
      model.update(toScore);
    }
    // First copy the input data to the new result...
    Object[] resultRow = RowDataUtil.resizeArray(inputRow, outputMeta.size());
    int index = inputMeta.size();

    // output for numeric class or discrete class value
    if (supervised) {
      if (classAtt.isNumeric()) {
        resultRow[index++] = prediction[0];
      } else {
        int maxProb = Utils.maxIndex(prediction);
        if (prediction[maxProb] > 0) {
          resultRow[index++] = classAtt.value(maxProb);
        } else {
          resultRow[index++] = BaseMessages
              .getString(PMIScoringMeta.PKG, "WekaScoringData.Message.UnableToPredict");
        }
      }
    } else {
      int maxProb = Utils.maxIndex(prediction);
      if (prediction[maxProb] > 0) {
        Double newVal = new Double(maxProb);
        resultRow[index++] = newVal;
      } else {
        String newVal = BaseMessages
            .getString(PMIScoringMeta.PKG, "PMIScoringData.Message.UnableToPredictCluster");
        resultRow[index++] = newVal;
      }
    }

    if ((outputProbs && classAtt == null) || (outputProbs && !classAtt.isNumeric())) {
      // output probability distribution
      for (int i = 0; i < prediction.length; i++) {
        Double newVal = prediction[i];
        resultRow[index++] = newVal;
      }
      int maxProb = Utils.maxIndex(prediction);
      resultRow[index] = prediction[maxProb];
    }

    return resultRow;
  }

  /**
   * Perform evaluation over a batch of instances (for BatchEvaluators).
   *
   * @param inputMeta the input row metadata
   * @param outputMeta the output row metadata
   * @param inputRows a list of inputRows to evaluate on. If null, this signals the end of
   * evaluation and a row containing evaluation metrics will be produced. If non-null, then
   * evaluation metrics will be updated by comparing the actual to predicted class values for the
   * input rows and null is returnd
   * @param meta step metadata for PMIScoring
   * @param vars variables to use
   * @return null if inputRows is non-null, or a row containing eval statistics if inputRows is
   * null.
   * @throws Exception if a problem occurs
   */
  public Object[][] evaluateForRows(IRowMeta inputMeta, IRowMeta outputMeta,
      List<Object[]> inputRows,
      PMIScoringMeta meta, IVariables vars) throws Exception {

    if (!getModel().isSupervisedLearningModel()) {
      throw new Exception("Model is not a supervised one");
    }

    Object[][] outputRow = null;

    if (inputRows == null || inputRows.size() == 0) {
      // end of input data - generate eval output row
      outputRow = new Object[1][0];
      outputRow[0] = m_eval.getEvalRow(null, -1, null);
    } else {
      Instances batch = new Instances(getModel().getHeader(), inputRows.size());
      for (Object[] r : inputRows) {
        Instance inst = constructInstance(batch, inputMeta, r, m_mappingIndexes, getModel(), true,
            true);
        batch.add(inst);
      }

      m_eval.setTrainedClassifier((Classifier) getModel().getModel());
      m_eval.performEvaluation(batch, new LogAdapter(meta.getLog()), new VariablesAdapter(vars));
    }

    return outputRow;
  }

  /**
   * Performs evaluation for a given input data row
   *
   * @param inputMeta the input row metadata
   * @param outputMeta the output row metadata
   * @param inputRow the actual input row. If null, this signals the end of evaluation and a row
   * containing evaluation metrics will be produced. If non-null, then evaluation metrics will be
   * updated by comparing the actual to predicted class value and null is returned
   * @param meta step metadata for PMIScoring
   * @return null if inputRow is non-null, or a row containing eval statistics if inputRow is null.
   * @throws Exception if a problem occurs
   */
  public Object[] evaluateForRow(IRowMeta inputMeta, IRowMeta outputMeta, Object[] inputRow,
      PMIScoringMeta meta) throws Exception {

    if (!getModel().isSupervisedLearningModel()) {
      throw new Exception("Model is not a supervised one");
    }

    Object[] outputRow = null;
    if (inputRow == null) {
      // end of input data - generate eval output row
      outputRow = m_eval.getEvalRow(null, -1, null);
    } else {
      Instance
          toPredict =
          constructInstance(getModel().getHeader(), inputMeta, inputRow, m_mappingIndexes,
              getModel(), false, false);
      m_eval.setTrainedClassifier((Classifier) getModel().getModel());
      m_eval.performEvaluationIncremental(toPredict, new LogAdapter( meta.getLog()));
    }

    return outputRow;
  }

  /**
   * Helper method that constructs an Instance to input to the PMI model based on incoming PDI
   * fields and pre-constructed attribute-to-field mapping data.
   *
   * @param header the header to use
   * @param inputMeta a <code>IRowMeta</code> value
   * @param inputRow an <code>Object</code> value
   * @param mappingIndexes an <code>int</code> value
   * @param model a <code>PMIScoringModel</code> value
   * @param freshVector true if a fresh array of doubles should be created (necessary for processing
   * batches for BatchPredictors)
   * @param addStringVals true to add string values (rather than setting) in the header. Again,
   * necessary for BatchPredictors
   * @return an <code>Instance</code> value
   */
  private Instance constructInstance(Instances header, IRowMeta inputMeta, Object[] inputRow,
      int[] mappingIndexes, PMIScoringModel model, boolean freshVector, boolean addStringVals) {

    // Instances header = model.getHeader();

    // Re-use this array (unless told otherwise) to avoid an object creation
    if (m_vals == null || freshVector) {
      m_vals = new double[header.numAttributes()];
    }

    for (int i = 0; i < header.numAttributes(); i++) {

      if (mappingIndexes[i] >= 0) {
        try {
          Object inputVal = inputRow[mappingIndexes[i]];

          Attribute temp = header.attribute(i);
          IValueMeta tempField = inputMeta.getValueMeta(mappingIndexes[i]);
          int fieldType = tempField.getType();

          // Check for missing value (null or empty string)
          if (tempField.isNull(inputVal)) {
            m_vals[i] = Utils.missingValue();
            continue;
          }

          switch (temp.type()) {
            case Attribute.NUMERIC:
              if (fieldType == IValueMeta.TYPE_BOOLEAN) {
                Boolean b = tempField.getBoolean(inputVal);
                if (b) {
                  m_vals[i] = 1.0;
                } else {
                  m_vals[i] = 0.0;
                }
              } else if (fieldType == IValueMeta.TYPE_INTEGER) {
                m_vals[i] = tempField.getInteger(inputVal);
              } else {
                m_vals[i] = tempField.getNumber(inputVal);
              }
              break;
            case Attribute.NOMINAL:
              String s = tempField.getString(inputVal);
              // now need to look for this value in the attribute
              // in order to get the correct index
              int index = temp.indexOfValue(s);
              if (index < 0) {
                // set to missing value
                m_vals[i] = Utils.missingValue();
              } else {
                m_vals[i] = index;
              }
              break;
            case Attribute.STRING: {
              String s2 = tempField.getString(inputVal);
              // Set the attribute in the header to contain just this string value
              if (addStringVals) {
                m_vals[i] = temp.addStringValue(s2);
              } else {
                temp.setStringValue(s2);
                m_vals[i] = 0.0;
              }
              break;
            }
            default:
              m_vals[i] = Utils.missingValue();
          }
        } catch (Exception e) {
          m_vals[i] = Utils.missingValue();
        }
      } else {
        // set to missing value
        m_vals[i] = Utils.missingValue();
      }
    }

    Instance newInst = new DenseInstance(1.0, m_vals);
    newInst.setDataset(header);
    return newInst;
  }

  public static boolean modelFileExists(String modelFile, IVariables space) throws Exception {

    modelFile = space.environmentSubstitute(modelFile);
    FileObject modelF = HopVfs.getFileObject(modelFile);

    return modelF.exists();
  }

}
