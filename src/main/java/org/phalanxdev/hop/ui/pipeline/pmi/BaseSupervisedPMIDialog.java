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

package org.phalanxdev.hop.ui.pipeline.pmi;

import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transform.stream.IStream;
import org.phalanxdev.hop.utils.ArffMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransformDialog;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.phalanxdev.hop.pipeline.transforms.pmi.BaseSupervisedPMIData;
import org.phalanxdev.hop.pipeline.transforms.pmi.BaseSupervisedPMIMeta;
import org.phalanxdev.mi.Evaluator;
import org.phalanxdev.mi.PMIEngine;
import org.phalanxdev.mi.Scheme;
import org.phalanxdev.mi.SchemeUtils;
import org.phalanxdev.mi.UnsupportedEngineException;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.ShowMessageDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import weka.core.Attribute;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.unsupervised.attribute.MergeInfrequentNominalValues;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.hop.core.Const.MARGIN;

/**
 * Base dialog class for PMI classification and regression steps.
 *
 * @author Mark Hall (mhall{[at]}waikato{[dot]}ac{[dot]}nz)
 * @version $Revision: $
 */
public class BaseSupervisedPMIDialog extends BaseTransformDialog implements ITransformDialog {

  private static Class<?> PKG = BaseSupervisedPMIMeta.class;

  protected CTabFolder m_container;

  /**
   * Individual tabs (Configure - engine selection & row handling opts); fields - input field & class; scheme - scheme opts & model output path stuff;
   * evaluation - eval stuff; preprocessing - preprocessing stuff
   */
  protected CTabItem m_configureTab, m_fieldsTab, m_schemeTab, m_evaluationTab, m_preprocessingTab;
  protected Composite m_configureComposite, m_fieldsComposite, m_schemeComposite, m_preprocessingComposite,
      m_evaluationComposite;

  /**
   * Group for scheme parameter widgets
   */
  protected Group m_schemeGroup;

  /**
   * Engine drop-down
   */
  protected ComboVar m_engineDropDown;

  /**
   * Rows to process drop-down
   */
  protected ComboVar m_rowsToProcessDropDown;

  /**
   * Stratification drop-down
   */
  protected ComboVar m_stratificationFieldDropDown;

  /**
   * Batch size field
   */
  protected TextVar m_batchSizeField;

  /**
   * Reservoir size field
   */
  protected TextVar m_reservoirSizeField;

  /**
   * Reservoir sampling checkbox
   */
  protected Button m_reservoirSamplingBut;

  /**
   * Random seed field
   */
  protected TextVar m_reservoirRandomSeedField;

  /**
   * Table for incoming fields & arff types
   */
  protected TableView m_fieldsTable;

  /**
   * Relation name for the training data
   */
  protected TextVar m_relationNameField;

  /**
   * Field for selecting the class/target
   */
  protected ComboVar m_classFieldDropDown;

  /**
   * Select upstream step for training data
   */
  protected ComboVar m_trainingStepDropDown;

  /**
   * Select upstream step for separate test set
   */
  protected ComboVar m_testStepDropDown;

  /**
   * Field for specifying the directory to save the model to
   */
  protected TextVar m_modelOutputDirectoryField;

  /**
   * Button for browsing to model files
   */
  protected Button m_browseModelOutputDirectoryButton;

  /**
   * Field for specifying the filename when saving the model
   */
  protected TextVar m_modelFilenameField;

  /**
   * For loading IterativeClassifiers for continued training
   */
  protected TextVar m_modelLoadField;
  protected Label m_modelLoadLab;
  protected Button m_browseLoadModelButton;

  /**
   * Field that appears only for incremental learning schemes - allows the initial n rows to be cached and used
   * initially to determine legal values for nominal fields before being passed to the scheme for training. This is
   * only necessary if there are incoming string fields for which the user intends to be treated as nominal but for
   * which they have not explicitly specified legal values.
   */
  protected TextVar m_incrementalRowCacheField;

  /**
   * Resample checkbox
   */
  protected Button m_resampleCheck;

  /**
   * Popup config for resampling
   */
  protected Button m_resampleConfig;

  /**
   * Checkbox for removing useless attributes
   */
  protected Button m_removeUselessCheck;

  /**
   * Popup config for remove useless filter
   */
  protected Button m_removeUselessConfig;

  /**
   * Remove infrequent values checkbox
   */
  protected Button m_mergeInfrequentValsCheck;

  /**
   * Popup config for remove infrequent values filter
   */
  protected Button m_mergeInfrequentValsConfig;

  /**
   * text vectorization checkbox
   */
  protected Button m_stringToWordVectorCheck;
  /**
   * Popup config for text vectorization options
   */
  protected Button m_stringToWordVectorConfig;

  /**
   * Select evaluation mode
   */
  protected ComboVar m_evalModeDropDown;

  /**
   * Percentage split to use
   */
  protected TextVar m_percentageSplitField;

  /**
   * Number of cross-validation folds to use
   */
  protected TextVar m_xValFoldsField;

  /**
   * Random seed to use for percentage split and x-val
   */
  protected TextVar m_randomSeedField;

  /**
   * Checkbox for outputting AUC metrics - if performing evaluation
   */
  protected Button m_outputAUCMetricsCheck;

  /**
   * Checkbox for outputting IR metrics - if performing evaluation
   */
  protected Button m_outputIRMetricsCheck;

  /**
   * Resample can be supervised or unsupervised - we have to switch based on the selected class type
   */
  protected Filter m_resample;

  protected RemoveUseless m_removeUselessFilter;
  protected MergeInfrequentNominalValues m_mergeInfrequentNominalValsFilter;
  protected StringToWordVector m_stringToWordVectorFilter;

  protected static int MIDDLE;
  protected static final int FIRST_LABEL_RIGHT_PERCENTAGE = 35;
  protected static final int FIRST_PROMPT_RIGHT_PERCENTAGE = 55; // 55
  protected static final int SECOND_LABEL_RIGHT_PERCENTAGE = 65; // 65
  protected static final int SECOND_PROMPT_RIGHT_PERCENTAGE = 80;
  protected static final int THIRD_PROMPT_RIGHT_PERCENTAGE = 90;

  protected static final int GOE_FIRST_BUTTON_RIGHT_PERCENTAGE = 70;
  protected static final int GOE_SECOND_BUTTON_RIGHT_PERCENTAGE = 80;

  private Control lastControl;

  protected BaseSupervisedPMIMeta m_originalMeta;
  protected BaseSupervisedPMIMeta m_inputMeta;

  /**
   * Current contents of the config scheme tab
   */
  protected Map<String, Object> m_schemeWidgets = new LinkedHashMap<>();

  /**
   * Holds references to the Labels that hold the textual representations of scheme parameters of type object and array
   */
  protected Map<String, Label> m_schemeObjectValueLabelTextReps = new LinkedHashMap<>();

  /**
   * The current scheme's info/paramemter metadata
   */
  protected Map<String, Object> m_topLevelSchemeInfo;

  /**
   * The map of properties from the top level scheme info
   */
  protected Map<String, Map<String, Object>> m_properties;

  /**
   * The actual top-level scheme
   */
  protected Scheme m_scheme;

  /**
   * Sampling (instance) filter(s) in use
   */
  protected List<Filter> m_samplingFilters;

  /**
   * Preprocessing (attribute) filters in use
   */
  protected List<Filter> m_preprocessingFilters;

  protected ModifyListener m_simpleModifyListener = new ModifyListener() {
    @Override public void modifyText( ModifyEvent modifyEvent ) {
      m_inputMeta.setChanged();
    }
  };

  protected SelectionAdapter m_simpleSelectionAdapter = new SelectionAdapter() {
    @Override public void widgetDefaultSelected( SelectionEvent selectionEvent ) {
      super.widgetDefaultSelected( selectionEvent );
      ok();
    }
  };

  public BaseSupervisedPMIDialog( Shell parent, IVariables variables, BaseTransformMeta baseTransformMeta,
      PipelineMeta pMeta, String transformname ) {
    super( parent, variables, baseTransformMeta, pMeta, transformname );
    m_inputMeta = (BaseSupervisedPMIMeta) baseTransformMeta;
    m_originalMeta = (BaseSupervisedPMIMeta) m_inputMeta.clone();
  }

  public BaseSupervisedPMIDialog( Shell parent, IVariables variables, Object inMeta, PipelineMeta tr,
      String transformName ) {
    super( parent, variables, (BaseTransformMeta) inMeta, tr, transformName );

    m_inputMeta = (BaseSupervisedPMIMeta) inMeta;
    m_originalMeta = (BaseSupervisedPMIMeta) m_inputMeta.clone();
  }

  public BaseSupervisedPMIDialog( Shell parent, int nr, IVariables variables, Object in, PipelineMeta tr ) {
    super( parent, nr, variables, (BaseTransformMeta) in, tr );
    m_inputMeta = (BaseSupervisedPMIMeta) in;
    m_originalMeta = (BaseSupervisedPMIMeta) m_inputMeta.clone();
  }

  @Override public String open() {

    // display, step name etc.
    initialDialogSetup();
    addConfigTab();
    addFieldsTab();
    addSchemeTab();
    addPreprocessingTab();
    addEvaluationTab();

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( wTransformName, MARGIN );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, -50 );
    m_container.setLayoutData( fd );

    // some buttons
    wOk = new Button( shell, SWT.PUSH );
    wOk.setText( BaseMessages.getString( PKG, "System.Button.OK" ) ); //$NON-NLS-1$
    wOk.addListener( SWT.Selection, new Listener() {
      @Override public void handleEvent( Event e ) {
        ok();
      }
    } );
    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PKG, "System.Button.Cancel" ) ); //$NON-NLS-1$
    wCancel.addListener( SWT.Selection, new Listener() {
      @Override public void handleEvent( Event e ) {
        cancel();
      }
    } );
    setButtonPositions( new Button[] { wOk, wCancel }, MARGIN, null );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    boolean okEngine = getData( m_inputMeta );
    populateSchemeTab( !okEngine, m_inputMeta );
    setEvaluationModeFromMeta( m_inputMeta );

    m_inputMeta.setChanged( changed );
    m_container.setSelection( 0 );

    m_container.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        if ( selectionEvent.item.equals( m_evaluationTab ) ) {
          checkWidgets();
        }
      }
    } );

    checkWidgets();
    getDataResumable( m_inputMeta );
    setSize();

    shell.open();

    Shell parent = getParent();
    Display display = parent.getDisplay();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }
    return transformName;
  }

  protected void setData( BaseSupervisedPMIMeta meta ) {
    meta.setEngineName( m_engineDropDown.getText() );
    meta.setRowsToProcess( m_rowsToProcessDropDown.getText() );
    meta.setBatchSize( m_batchSizeField.getText() );
    meta.setUseReservoirSampling( m_reservoirSamplingBut.getSelection() );
    meta.setReservoirSize( m_reservoirSizeField.getText() );
    meta.setRandomSeedReservoirSampling( m_reservoirRandomSeedField.getText() );

    meta.setTrainingStepInputName( m_trainingStepDropDown.getText() );
    if ( m_evalModeDropDown.getText().equalsIgnoreCase( Evaluator.EvalMode.SEPARATE_TEST_SET.toString() ) ) {
      meta.setTestingStepInputName( m_testStepDropDown.getText() );
    } else {
      meta.setTestingStepInputName( "" );
    }
    meta.setClassField( m_classFieldDropDown.getText() );
    meta.setStratificationFieldName( m_stratificationFieldDropDown.getText() );

    meta.clearStepIOMeta();
    List<IStream> infoStreams = meta.getStepIOMeta().getInfoStreams();
    String trainingStepName = meta.getTrainingStepInputName();
    if ( !org.apache.hop.core.util.Utils.isEmpty( trainingStepName ) ) {
      if ( infoStreams.size() > 0 ) {
        infoStreams.get( 0 ).setSubject( trainingStepName );
      }
    }
    if ( m_evalModeDropDown.getText().equalsIgnoreCase( Evaluator.EvalMode.SEPARATE_TEST_SET.toString() ) ) {
      String testStepName = meta.getTestingStepInputName();
      if ( infoStreams.size() > 1 ) {
        infoStreams.get( 1 ).setSubject( testStepName );
      }
    }

    List<ArffMeta> userFields = new ArrayList<>();
    int numNonEmpty = m_fieldsTable.nrNonEmpty();
    for ( int i = 0; i < numNonEmpty; i++ ) {
      TableItem item = m_fieldsTable.getNonEmpty( i );

      String fieldName = item.getText( 1 );
      int hopType = ValueMetaFactory.getIdForValueMeta( item.getText( 2 ) );
      int arffType = getArffTypeInt( item.getText( 3 ) );
      String nomVals = item.getText( 4 );
      ArffMeta newArffMeta = new ArffMeta( fieldName, hopType, arffType );
      if ( !org.apache.hop.core.util.Utils.isEmpty( nomVals ) ) {
        newArffMeta.setNominalVals( nomVals );
      }
      userFields.add( newArffMeta );
    }
    meta.setFieldMetadata( userFields );

    meta.setModelOutputPath( m_modelOutputDirectoryField.getText() );
    meta.setModelFileName( m_modelFilenameField.getText() );

    // preprocessing filters
    m_samplingFilters.clear();
    m_preprocessingFilters.clear();
    if ( m_resampleCheck.getSelection() ) {
      m_samplingFilters.add( m_resample );
    }
    if ( m_removeUselessCheck.getSelection() ) {
      m_preprocessingFilters.add( m_removeUselessFilter );
    }
    if ( m_mergeInfrequentValsCheck.getSelection() ) {
      m_preprocessingFilters.add( m_mergeInfrequentNominalValsFilter );
    }
    if ( m_stringToWordVectorCheck.getSelection() ) {
      m_preprocessingFilters.add( m_stringToWordVectorFilter );
    }
    meta.setSamplingConfigs( SchemeUtils.filterListToConfigs( m_samplingFilters ) );
    meta.setPreprocessingConfigs( SchemeUtils.filterListToConfigs( m_preprocessingFilters ) );

    String evalMode = m_evalModeDropDown.getText();
    Evaluator.EvalMode toSet = Evaluator.EvalMode.NONE;
    for ( Evaluator.EvalMode e : Evaluator.EvalMode.values() ) {
      if ( evalMode.equalsIgnoreCase( e.toString() ) ) {
        toSet = e;
        break;
      }
    }
    meta.setEvalMode( toSet );

    meta.setXValFolds( m_xValFoldsField.getText() );
    meta.setPercentageSplit( m_percentageSplitField.getText() );
    meta.setRandomSeed( m_randomSeedField.getText() );
    meta.setOutputAUCMetrics( m_outputAUCMetricsCheck.getSelection() );
    meta.setOutputIRMetrics( m_outputIRMetricsCheck.getSelection() );

    // Algorithm options - populates the 'properties' map from the widgets and then sets these
    // values on the scheme itself
    GOEDialog.widgetValuesToPropsMap( m_scheme, m_topLevelSchemeInfo, m_schemeWidgets );

    String[] schemeOpts = m_scheme.getSchemeOptions();
    if ( schemeOpts != null && schemeOpts.length > 0 ) {
      meta.setSchemeCommandLineOptions( Utils.joinOptions( schemeOpts ) );
    } else {
      meta.setSchemeCommandLineOptions( "" );
    }

    if ( m_incrementalRowCacheField != null ) {
      meta.setInitialRowCacheForNominalValDetermination( m_incrementalRowCacheField.getText() );
    }

    if ( m_modelLoadField != null ) {
      meta.setResumableModelPath( m_modelLoadField.getText() );
    } else {
      meta.setResumableModelPath( "" );
    }
  }

  /**
   * Convert ARFF type to an integer code
   *
   * @param arffType the ARFF data type as a String
   * @return the ARFF data type as an integer (as defined in ArffMeta
   */
  private static int getArffTypeInt( String arffType ) {
    if ( arffType.equalsIgnoreCase( "Numeric" ) ) { //$NON-NLS-1$
      return ArffMeta.NUMERIC;
    }
    if ( arffType.equalsIgnoreCase( "Nominal" ) ) { //$NON-NLS-1$
      return ArffMeta.NOMINAL;
    }
    if ( arffType.equalsIgnoreCase( "String" ) ) { //$NON-NLS-1$
      return ArffMeta.STRING;
    }
    return ArffMeta.DATE;
  }

  protected void getDataResumable( BaseSupervisedPMIMeta meta ) {
    if ( m_modelLoadField != null ) {
      m_modelLoadField.setText( meta.getResumableModelPath() );
    }
  }

  protected boolean getData( BaseSupervisedPMIMeta meta ) {
    boolean engineOK = true;
    List<String> availEngines = Arrays.asList( m_engineDropDown.getItems() );
    if ( availEngines.contains( meta.getEngineName() ) ) {
      m_engineDropDown.setText( meta.getEngineName() );
    } else {
      m_engineDropDown.setText( m_engineDropDown.getItems()[0] );
      engineOK = false;
    }
    m_rowsToProcessDropDown.setText( meta.getRowsToProcess() );
    m_batchSizeField.setText( meta.getBatchSize() );
    m_reservoirSamplingBut.setSelection( meta.getUseReservoirSampling() );
    m_reservoirSizeField.setText( meta.getReservoirSize() );
    m_reservoirRandomSeedField.setText( meta.getRandomSeedReservoirSampling() );

    m_trainingStepDropDown.setText( meta.getTrainingStepInputName() );
    m_testStepDropDown.setText( meta.getTestingStepInputName() );
    m_classFieldDropDown.setText( meta.getClassField() );
    m_stratificationFieldDropDown.setText( meta.getStratificationFieldName() );

    List<ArffMeta> userFields = meta.getFieldMetadata();
    if ( userFields.size() > 0 ) {
      m_fieldsTable.clearAll();

      for ( ArffMeta m : userFields ) {
        TableItem item = new TableItem( m_fieldsTable.table, SWT.NONE );
        item.setText( 1, org.apache.hop.core.Const.NVL( m.getFieldName(), "" ) );
        item.setText( 2, org.apache.hop.core.Const.NVL( ValueMetaFactory.getValueMetaName( m.getHopType() ), "" ) );
        item.setText( 3, org.apache.hop.core.Const.NVL( BaseSupervisedPMIData.typeToString( m.getArffType() ), "" ) );
        if ( !org.apache.hop.core.util.Utils.isEmpty( m.getNominalVals() ) ) {
          item.setText( 4, m.getNominalVals() );
        }
      }

      m_fieldsTable.removeEmptyRows();
      m_fieldsTable.setRowNums();
      m_fieldsTable.optWidth( true );
    }

    if ( !org.apache.hop.core.util.Utils.isEmpty( m_trainingStepDropDown.getText() ) ) {
      populateClassAndStratCombos();
    }

    // Algo config is taken care of already
    if ( !org.apache.hop.core.util.Utils.isEmpty( meta.getModelOutputPath() ) ) {
      m_modelOutputDirectoryField.setText( meta.getModelOutputPath() );
    }

    if ( !org.apache.hop.core.util.Utils.isEmpty( meta.getModelFileName() ) ) {
      m_modelFilenameField.setText( meta.getModelFileName() );
    }

    // Preprocessing
    // sets options on these filters based on what is stored in meta. Also sets the status of the
    // checkboxes for each filter
    try {
      setOptionsForPreprocessingFromMeta( meta );
    } catch ( Exception e ) {
      e.printStackTrace();
      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              e.getMessage(), false );
      smd.open();
    }

    // Evaluation
    m_xValFoldsField.setText( meta.getXValFolds() );
    m_percentageSplitField.setText( meta.getPercentageSplit() );
    m_randomSeedField.setText( meta.getRandomSeed() );
    m_outputAUCMetricsCheck.setSelection( meta.getOutputAUCMetrics() );
    m_outputIRMetricsCheck.setSelection( meta.getOutputIRMetrics() );

    return engineOK;
  }

  protected void initialDialogSetup() {
    Shell parent = getParent();
    Display display = parent.getDisplay();

    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN );
    props.setLook( shell );
    setShellImage( shell, m_inputMeta );

    changed = m_inputMeta.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;
    shell.setLayout( formLayout );
    shell.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Shell.Title", m_inputMeta.getSchemeName() ) );

    MIDDLE = props.getMiddlePct();

    // Stepname line
    wlTransformName = new Label( shell, SWT.RIGHT );
    wlTransformName.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stepname.Label" ) ); //$NON-NLS-1$
    props.setLook( wlTransformName );
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment( 0, 0 );
    fdlTransformName.right = new FormAttachment( MIDDLE, -MARGIN );
    fdlTransformName.top = new FormAttachment( 0, MARGIN );
    wlTransformName.setLayoutData( fdlTransformName );
    wTransformName = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    wTransformName.setText( transformName );
    props.setLook( wTransformName );
    wTransformName.addModifyListener( m_simpleModifyListener );
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment( MIDDLE, 0 );
    fdTransformName.top = new FormAttachment( 0, MARGIN );
    fdTransformName.right = new FormAttachment( 100, 0 );
    wTransformName.setLayoutData( fdTransformName );
    lastControl = wTransformName;

    m_container = new CTabFolder( shell, SWT.BORDER );
    props.setLook( m_container, Props.WIDGET_STYLE_TAB );
    m_container.setSimple( false );
  }

  protected void addConfigTab() {
    m_configureTab = new CTabItem( m_container, SWT.NONE );
    m_configureTab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.TabTitle" ) );
    m_configureComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_configureComposite );
    FormLayout fl = new FormLayout();
    fl.marginWidth = 3;
    fl.marginHeight = 3;
    m_configureComposite.setLayout( fl );

    // engine group
    Group engGroup = new Group( m_configureComposite, SWT.SHADOW_NONE );
    props.setLook( engGroup );
    engGroup.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.EngineGroup" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    engGroup.setLayout( fl );
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( 0, 0 );
    engGroup.setLayoutData( fd );

    Label engineLab = new Label( engGroup, SWT.RIGHT );
    engineLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Engine.Label" ) );
    props.setLook( engineLab );
    engineLab.setLayoutData( getFirstLabelFormData() );

    m_engineDropDown = new ComboVar( variables, engGroup, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_engineDropDown );
    m_engineDropDown.setEditable( false );
    m_engineDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        checkWidgets();
        populateSchemeTab( true, m_inputMeta );
        checkWidgets();
      }
    } );

    m_engineDropDown.setLayoutData( getFirstPromptFormData( engineLab ) );
    List<String> engineNames = PMIEngine.getEngineNames();
    List<String> engineProbsExceptions = new ArrayList<>();
    List<String> problematicEngines = new ArrayList<>();
    String schemeName = m_originalMeta.getSchemeName();
    for ( String engineN : engineNames ) {
      try {
        PMIEngine eng = PMIEngine.getEngine( engineN );
        if ( eng.engineAvailable( engineProbsExceptions ) ) {
          if ( eng.supportsScheme( schemeName ) ) {
            m_engineDropDown.add( engineN );
          }
        } else {
          problematicEngines.add( eng.engineName() );
        }
      } catch ( UnsupportedEngineException e ) {
        e.printStackTrace();
        engineProbsExceptions.add( e.getMessage() );
        problematicEngines.add( engineN );
      }
    }

    if ( problematicEngines.size() > 0 ) {
      StringBuilder b = new StringBuilder();
      for ( String n : problematicEngines ) {
        b.append( n ).append( " " );
      }
      showMessageDialog( BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnavailableEngineTitle" ),
          BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnavailableEngineMessage", b.toString() ),
          SWT.OK | SWT.ICON_INFORMATION, true );
    }

    // row handling group
    Group rowGroup = new Group( m_configureComposite, SWT.SHADOW_NONE );
    props.setLook( rowGroup );
    rowGroup.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.RowHandlingGroup" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    rowGroup.setLayout( fl );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( engGroup, MARGIN );
    rowGroup.setLayoutData( fd );

    Label rowsToProcLab = new Label( rowGroup, SWT.RIGHT );
    rowsToProcLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RowsToProcess.Label" ) );
    props.setLook( rowsToProcLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( FIRST_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( 0, MARGIN );
    rowsToProcLab.setLayoutData( fd );

    m_rowsToProcessDropDown = new ComboVar( variables, rowGroup, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_rowsToProcessDropDown );
    m_rowsToProcessDropDown.setEditable( false );
    m_rowsToProcessDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        handleRowsToProcessChange();
      }
    } );
    m_rowsToProcessDropDown.setLayoutData( getFirstPromptFormData( rowsToProcLab ) );
    m_rowsToProcessDropDown
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.TipText" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.AllEntry.Label" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.BatchEntry.Label" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.StratifiedEntry.Label" ) );

    Label rowsToProcessSizeLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( rowsToProcessSizeLab );
    rowsToProcessSizeLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcessSize.Label" ) );
    rowsToProcessSizeLab.setLayoutData( getSecondLabelFormData( m_rowsToProcessDropDown ) );

    m_batchSizeField = new TextVar( variables, rowGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_batchSizeField );
    m_batchSizeField.addModifyListener( m_simpleModifyListener );
    m_batchSizeField.setLayoutData( getSecondPromptFormData( rowsToProcessSizeLab ) );
    m_batchSizeField.setEnabled( false );
    lastControl = m_batchSizeField;

    // reservoir sampling
    Label reservoirSamplingLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( reservoirSamplingLab );
    reservoirSamplingLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSampling.Label" ) );
    reservoirSamplingLab.setLayoutData( getFirstLabelFormData() );

    m_reservoirSamplingBut = new Button( rowGroup, SWT.CHECK );
    props.setLook( m_reservoirSamplingBut );
    fd = getFirstPromptFormData( reservoirSamplingLab );
    fd.right = null;
    m_reservoirSamplingBut.setLayoutData( fd );
    m_reservoirSamplingBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        handleReservoirSamplingChange();
      }
    } );
    m_reservoirSamplingBut
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSampling.TipText" ) );

    Label reservoirSamplingSizeLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( reservoirSamplingSizeLab );
    reservoirSamplingSizeLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSamplingSize.Label" ) );
    reservoirSamplingSizeLab.setLayoutData( getSecondLabelFormData( m_reservoirSamplingBut ) );

    m_reservoirSizeField = new TextVar( variables, rowGroup, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_reservoirSizeField );
    m_reservoirSizeField
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSamplingSize.TipText" ) );
    m_reservoirSizeField.setLayoutData( getSecondPromptFormData( reservoirSamplingSizeLab ) );
    m_reservoirSizeField.setEnabled( false );
    lastControl = m_reservoirSizeField;
    m_reservoirSizeField.addModifyListener( new ModifyListener() {
      @Override public void modifyText( ModifyEvent modifyEvent ) {
        m_inputMeta.setChanged();
      }
    } );

    Label randomSeedLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( randomSeedLab );
    randomSeedLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeedReservoirSampling.Label" ) );
    randomSeedLab.setLayoutData( getFirstLabelFormData() );

    m_reservoirRandomSeedField = new TextVar( variables, rowGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_reservoirRandomSeedField );
    m_reservoirRandomSeedField
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeedReservoirSampling.TipText" ) );
    m_reservoirRandomSeedField.setLayoutData( getFirstPromptFormData( randomSeedLab ) );
    m_reservoirRandomSeedField.setEnabled( true );
    lastControl = m_reservoirRandomSeedField;

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_configureComposite.setLayoutData( fd );
    m_configureComposite.layout();

    m_configureTab.setControl( m_configureComposite );
  }

  protected void addFieldsTab() {

    m_fieldsTab = new CTabItem( m_container, SWT.NONE );
    m_fieldsTab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.FieldsTab.Title" ) );

    m_fieldsComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_fieldsComposite );
    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_fieldsComposite.setLayout( fl );

    Label fieldsTableLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( fieldsTableLab );
    fieldsTableLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.FieldsTable.Label" ) );

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, MARGIN );
    fieldsTableLab.setLayoutData( fd );

    // Stratification field
    Label stratificationLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( stratificationLab );
    stratificationLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stratification.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( 100, -MARGIN * 2 );
    stratificationLab.setLayoutData( fd );

    m_stratificationFieldDropDown = new ComboVar( variables, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    m_stratificationFieldDropDown.setEditable( true );
    props.setLook( m_stratificationFieldDropDown );
    fd = getFirstPromptFormData( stratificationLab );
    fd.top = null;
    fd.bottom = new FormAttachment( 100, -MARGIN * 2 );
    m_stratificationFieldDropDown.setLayoutData( fd );
    m_stratificationFieldDropDown
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stratification.TipText" ) );

    // class field
    Label classLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( classLab );
    classLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Class.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_stratificationFieldDropDown, -MARGIN );
    classLab.setLayoutData( fd );

    m_classFieldDropDown = new ComboVar( variables, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    m_classFieldDropDown.setEditable( true );
    props.setLook( m_classFieldDropDown );
    fd = getFirstPromptFormData( classLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_stratificationFieldDropDown, -MARGIN );
    m_classFieldDropDown.setLayoutData( fd );

    // separate test set step field
    Label separateTestLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( separateTestLab );
    separateTestLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.SeparateTest.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_classFieldDropDown, -MARGIN );
    separateTestLab.setLayoutData( fd );

    m_testStepDropDown = new ComboVar( variables, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_testStepDropDown );
    fd = getFirstPromptFormData( separateTestLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_classFieldDropDown, -MARGIN );
    m_testStepDropDown.setLayoutData( fd );

    // training set step field
    Label trainingLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( trainingLab );
    trainingLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Training.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_testStepDropDown, -MARGIN );
    trainingLab.setLayoutData( fd );

    m_trainingStepDropDown = new ComboVar( variables, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_trainingStepDropDown );
    fd = getFirstPromptFormData( trainingLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_testStepDropDown, -MARGIN );
    m_trainingStepDropDown.setLayoutData( fd );
    m_trainingStepDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        checkWidgets();
        populateClassAndStratCombos();
      }
    } );

    String[] previousStepNames = pipelineMeta.getPrevTransformNames( transformName );
    if ( previousStepNames != null ) {
      for ( String name : previousStepNames ) {
        m_trainingStepDropDown.add( name );
        m_testStepDropDown.add( name );
      }
    }

    wGet = new Button( m_fieldsComposite, SWT.PUSH );
    props.setLook( wGet );
    wGet.setText( BaseMessages.getString( PKG, "System.Button.GetFields" ) );
    wGet.setToolTipText( BaseMessages.getString( PKG, "System.Tooltip.GetFields" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( MIDDLE, -MARGIN );
    fd.bottom = new FormAttachment( m_trainingStepDropDown, -MARGIN );
    wGet.setLayoutData( fd );
    wGet.setEnabled( true );

    wGet.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        populateFieldsTable();
      }
    } );

    final int fieldsRows = 1;

    ColumnInfo[]
        colinf =
        new ColumnInfo[] { new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.Name" ),
            ColumnInfo.COLUMN_TYPE_TEXT, false ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.KettleType" ),
                ColumnInfo.COLUMN_TYPE_TEXT, false ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.ArffType" ),
                ColumnInfo.COLUMN_TYPE_CCOMBO, true ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepFlowDialog.OutputFieldsColumn.NomVals" ),
                ColumnInfo.COLUMN_TYPE_TEXT, false ) };
    colinf[0].setReadOnly( true );
    colinf[1].setReadOnly( true );
    colinf[2].setReadOnly( false );
    colinf[3].setReadOnly( false );

    colinf[2].setComboValues( new String[] { BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.Numeric" ),
        BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.Nominal" ),
        BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.String" ) } );

    m_fieldsTable =
        new TableView( variables, m_fieldsComposite, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI, colinf, fieldsRows,
            new ModifyListener() {
              @Override public void modifyText( ModifyEvent modifyEvent ) {
                m_inputMeta.setChanged();
              }
            }, props );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( fieldsTableLab, MARGIN );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( wGet, -MARGIN * 2 );
    m_fieldsTable.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_fieldsComposite.setLayoutData( fd );
    m_fieldsComposite.layout();

    m_fieldsTab.setControl( m_fieldsComposite );

    // TODO set enabled status of various stuff (after getData())
  }

  protected void addSchemeTab() {
    m_schemeTab = new CTabItem( m_container, SWT.NONE );
    m_schemeTab.setText( BaseMessages.getString( PKG, "BasePMIStep.SchemeTab.Title" ) );

    m_schemeComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_schemeComposite );
    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_schemeComposite.setLayout( fl );

    m_schemeGroup = new Group( m_schemeComposite, SWT.SHADOW_NONE );
    props.setLook( m_schemeGroup );
    m_schemeGroup.setText(
        m_inputMeta.getSchemeName() + ( org.apache.hop.core.util.Utils.isEmpty( m_engineDropDown.getText() ) ? "" :
            " (" + m_engineDropDown.getText() + ")" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    m_schemeGroup.setLayout( fl );
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( 0, 0 );
    m_schemeGroup.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_schemeComposite.setLayoutData( fd );
    m_schemeComposite.layout();

    Label modelOutputDirectoryLab = new Label( m_schemeComposite, SWT.RIGHT );
    props.setLook( modelOutputDirectoryLab );
    modelOutputDirectoryLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputModelDirectory.Label" ) );
    lastControl = m_schemeGroup;
    modelOutputDirectoryLab.setLayoutData( getFirstLabelFormData() );

    m_modelOutputDirectoryField = new TextVar( variables, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_modelOutputDirectoryField );
    m_modelOutputDirectoryField.setLayoutData( getFirstPromptFormData( modelOutputDirectoryLab ) );

    m_browseModelOutputDirectoryButton = new Button( m_schemeComposite, SWT.PUSH );
    props.setLook( m_browseModelOutputDirectoryButton );
    m_browseModelOutputDirectoryButton
        .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.BrowseModelOutputDirectory.Button" ) );
    m_browseModelOutputDirectoryButton.setLayoutData( getSecondLabelFormData( m_modelOutputDirectoryField ) );

    m_browseModelOutputDirectoryButton.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        DirectoryDialog dialog = new DirectoryDialog( shell, SWT.SAVE );

        if ( !org.apache.hop.core.util.Utils.isEmpty( m_modelOutputDirectoryField.getText() ) ) {
          boolean ok = false;
          String outputDir = variables.resolve( m_modelOutputDirectoryField.getText() );
          File updatedPath = null;
          if ( outputDir.toLowerCase().startsWith( "file:" ) ) {
            outputDir = outputDir.replace( " ", "%20" );
            try {
              updatedPath = new File( new java.net.URI( outputDir ) );
              ok = true;
            } catch ( URISyntaxException e ) {
              e.printStackTrace();
            }
          } else {
            updatedPath = new File( outputDir );
            ok = true;
          }
          if ( ok && updatedPath.exists() && updatedPath.isDirectory() ) {
            dialog.setFilterPath( updatedPath.toString() );
          }
        }

        String selectedDirectory = dialog.open();
        if ( selectedDirectory != null ) {
          m_modelOutputDirectoryField.setText( selectedDirectory );
        }
      }
    } );
    lastControl = m_modelOutputDirectoryField;

    Label modelOutputFilenameLab = new Label( m_schemeComposite, SWT.RIGHT );
    modelOutputFilenameLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputModelFilename.Label" ) );
    props.setLook( modelOutputDirectoryLab );
    modelOutputFilenameLab.setLayoutData( getFirstLabelFormData() );

    m_modelFilenameField = new TextVar( variables, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_modelFilenameField );
    m_modelFilenameField.setLayoutData( getFirstPromptFormData( modelOutputFilenameLab ) );

    m_schemeTab.setControl( m_schemeComposite );
  }

  @SuppressWarnings( "unchecked" ) protected void refreshSchemeLabels() {
    if ( m_scheme != null ) {
      Map<String, Map<String, Object>>
          properties =
          (Map<String, Map<String, Object>>) m_topLevelSchemeInfo.get( "properties" );

      for ( Map.Entry<String, Map<String, Object>> e : properties.entrySet() ) {
        String propName = e.getKey();
        Map<String, Object> propDetails = e.getValue();
        String type = (String) propDetails.get( "type" );
        String propLabelText = (String) propDetails.get( "label" );
        Object value = propDetails.get( "value" );

        if ( type.equalsIgnoreCase( "object" ) ) {
          // refresh the value label for this option
          Label toRefresh = m_schemeObjectValueLabelTextReps.get( propName );
          if ( toRefresh != null ) {
            String textualRep = value != null ? value.toString() : "";
            toRefresh.setText( textualRep );
          }
        } else if ( type.equalsIgnoreCase( "array" ) ) {
          Label toRefresh = m_schemeObjectValueLabelTextReps.get( propName );
          if ( toRefresh != null ) {
            String arrayType = (String) propDetails.get( "array-type" );
            Object objectValue = propDetails.get( "objectValue" );
            if ( arrayType != null && arrayType.length() > 0 && arrayType.equalsIgnoreCase( "object" ) ) {
              toRefresh.setText( value.toString() + " : " + Array.getLength( objectValue ) );
            }
          }
        }
      }
    }
  }

  @SuppressWarnings( "unchecked" ) protected void buildPropertySheet() {

    for ( Control k : m_schemeGroup.getChildren() ) {
      k.dispose();
    }
    m_schemeWidgets.clear();
    m_schemeObjectValueLabelTextReps.clear();

    lastControl = null;
    String helpInfo = (String) m_topLevelSchemeInfo.get( "helpSummary" );
    String helpSynopsis = (String) m_topLevelSchemeInfo.get( "helpSynopsis" );
    if ( !org.apache.hop.core.util.Utils.isEmpty( helpInfo ) ) {
      Group helpGroup = new Group( m_schemeGroup, SWT.SHADOW_NONE );
      props.setLook( helpGroup );
      helpGroup.setText( "About" );
      FormLayout fl = new FormLayout();
      fl.marginWidth = 10;
      fl.marginHeight = 10;
      helpGroup.setLayout( fl );
      FormData fd = new FormData();
      fd.left = new FormAttachment( 0, 0 );
      fd.right = new FormAttachment( 100, 0 );
      fd.top = new FormAttachment( 0, 0 );
      helpGroup.setLayoutData( fd );

      // TODO do this properly at some stage...
      Button moreButton = null;
          /* if ( !org.apache.hop.core.util.Utils.isEmpty( helpSynopsis ) ) {
            moreButton = new Button( helpGroup, SWT.PUSH );
            props.setLook( moreButton );
            moreButton.setText( "More..." );
            fd = new FormData();
            fd.top = new FormAttachment( 0, 4 );
            fd.right = new FormAttachment( 100, -4 );
            moreButton.setLayoutData( fd );

            moreButton.addSelectionListener( new SelectionAdapter() {
              @Override public void widgetSelected( SelectionEvent selectionEvent ) {
                // TODO popup "more" window
              }
            } );
          } */

      Label aboutLab = new Label( helpGroup, SWT.LEFT );
      props.setLook( aboutLab );
      aboutLab.setText( helpInfo );
      fd = new FormData();
      fd.top = new FormAttachment( 0, 4 );
      fd.left = new FormAttachment( 0, 0 );
      fd.right = moreButton != null ? new FormAttachment( moreButton, -4 ) : new FormAttachment( 100, -4 );
      aboutLab.setLayoutData( fd );
      lastControl = helpGroup;
    }

    m_properties = (Map<String, Map<String, Object>>) m_topLevelSchemeInfo.get( "properties" );

    Set<String> propGroupings = new LinkedHashSet<>();
    for ( Map.Entry<String, Map<String, Object>> e : m_properties.entrySet() ) {
      Map<String, Object> propDetails = e.getValue();
      String category = (String) propDetails.get( "category" );
      if ( category != null && category.length() > 0 ) {
        propGroupings.add( category );
      }
    }

    for ( Map.Entry<String, Map<String, Object>> e : m_properties.entrySet() ) {
      final String propName = e.getKey();
      final Map<String, Object> propDetails = e.getValue();
      String tipText = (String) propDetails.get( "tip-text" );
      String type = (String) propDetails.get( "type" );
      String propLabelText = (String) propDetails.get( "label" );
      final Object value = propDetails.get( "value" );
      String category = (String) propDetails.get( "category" );

      if ( category != null && category.length() > 0 ) {
        // skip, and we'll create a button for each category later
        continue;
      }

      Label propLabel = new Label( m_schemeGroup, SWT.RIGHT );
      props.setLook( propLabel );
      propLabel.setText( propLabelText );
      if ( !org.apache.hop.core.util.Utils.isEmpty( tipText ) ) {
        propLabel.setToolTipText( tipText );
      }
      propLabel.setLayoutData( getFirstLabelFormData() );

      // everything apart from object, array and pick-list is handled by a text field
      if ( type.equalsIgnoreCase( "object" ) ) {
        String objectTextRep = value.toString();
        Object objectValue = propDetails.get( "objectValue" );
        final String goeBaseType = propDetails.get( "goeBaseType" ).toString();
        final Label objectValueLab = new Label( m_schemeGroup, SWT.RIGHT );
        props.setLook( objectValueLab );
        objectValueLab.setText( objectTextRep );
        objectValueLab.setLayoutData( getFirstPromptFormData( propLabel ) );
        m_schemeObjectValueLabelTextReps.put( propName, objectValueLab );

        final Button objectValEditBut = new Button( m_schemeGroup, SWT.PUSH );
        props.setLook( objectValEditBut );
        objectValEditBut.setText( "Edit..." /*+ objectTextRep */ );
        // objectValEditBut.setLayoutData( getSecondPromptFormData( objectValueLab ) );
        objectValEditBut.setLayoutData( getFirstGOEFormData( objectValueLab ) );

        final Button objectChooseBut = new Button( m_schemeGroup, SWT.PUSH );
        props.setLook( objectChooseBut );
        objectChooseBut.setText( "Choose..." );
        // objectChooseBut.setLayoutData( getThirdPropmtFormData( objectValEditBut ) );
        objectChooseBut.setLayoutData( getSecondGOEFormData( objectValEditBut ) );
        objectChooseBut.addSelectionListener( new SelectionAdapter() {
          @Override public void widgetSelected( SelectionEvent selectionEvent ) {
            super.widgetSelected( selectionEvent );
            Object selectedObject = null;
            try {
              objectChooseBut.setEnabled( false );
              objectValEditBut.setEnabled( false );
              GOETree treeDialog = new GOETree( shell, SWT.OK | SWT.CANCEL, goeBaseType );
              int result = treeDialog.open();
              if ( result == SWT.OK ) {
                Object selectedTreeValue = treeDialog.getSelectedTreeObject();
                if ( selectedTreeValue != null ) {
                  Map<String, Object> propDetails = m_properties.get( propName );
                  if ( propDetails != null ) {
                    propDetails.put( "objectValue", selectedTreeValue );
                  }

                  // This is solely in case there is a dependency between options, i.e. where changing the value of
                  // one option causes another one to change.
                  m_scheme.setSchemeParameters( m_properties );
                  m_topLevelSchemeInfo = m_scheme.getSchemeInfo();
                  m_properties = (Map<String, Map<String, Object>>) m_topLevelSchemeInfo.get( "properties" );
                  refreshSchemeLabels();
                }
                objectValueLab.setText( SchemeUtils.getTextRepresentationOfObjectValue( selectedTreeValue ) );
              }
            } catch ( Exception ex ) {
              // TODO popup error dialog
              ex.printStackTrace();
            } finally {
              objectChooseBut.setEnabled( true );
              objectValEditBut.setEnabled( true );
            }
          }
        } );

        objectValEditBut.addSelectionListener( new SelectionAdapter() {
          @Override public void widgetSelected( SelectionEvent selectionEvent ) {
            super.widgetSelected( selectionEvent );
            objectValEditBut.setEnabled( false );
            objectChooseBut.setEnabled( false );
            try {
              // re-get the prop details here in case changing another object-based option has resulted in
              // a change to this one due to a dependency
              Map<String, Object> propDetails = m_properties.get( propName );
              GOEDialog
                  dialog =
                  new GOEDialog( shell, SWT.OK | SWT.CANCEL, propDetails.get( "objectValue" ), variables );
              dialog.open();

              objectValueLab
                  .setText( SchemeUtils.getTextRepresentationOfObjectValue( propDetails.get( "objectValue" ) ) );

              // This is solely in case there is a dependency between options, i.e. where changing the value of
              // one option causes another one to change.
              //m_scheme.setSchemeParameters( m_properties );
              // m_topLevelSchemeInfo = m_scheme.getSchemeInfo();
              // refreshSchemeLabels();
            } catch ( Exception e1 ) {
              e1.printStackTrace();
            } finally {
              objectValEditBut.setEnabled( true );
              objectChooseBut.setEnabled( true );
            }
          }
        } );

        lastControl = objectValEditBut;
      } else if ( type.equalsIgnoreCase( "array" ) ) {
        String arrayType = (String) propDetails.get( "array-type" );
        Object objectValue = propDetails.get( "objectValue" );
        if ( arrayType != null && arrayType.length() > 0 && arrayType.equalsIgnoreCase( "object" ) ) {
          // just handle objects for now...
          // value holds element type class : num elements in array
          // objectValue holds the array itself

          final Label arrayElementType = new Label( m_schemeGroup, SWT.RIGHT );
          props.setLook( arrayElementType );
          arrayElementType.setText( value.toString() + " : " + Array.getLength( objectValue ) );
          arrayElementType.setLayoutData( getFirstPromptFormData( propLabel ) );
          m_schemeObjectValueLabelTextReps.put( propName, arrayElementType );

          final Button arrayValEditBut = new Button( m_schemeGroup, SWT.PUSH );
          props.setLook( arrayValEditBut );
          arrayValEditBut.setText( "Edit..." );
          arrayValEditBut.setLayoutData( getFirstGOEFormData( arrayElementType ) );

          lastControl = arrayValEditBut;

          arrayValEditBut.addSelectionListener( new SelectionAdapter() {
            @Override public void widgetSelected( SelectionEvent selectionEvent ) {
              super.widgetSelected( selectionEvent );
              // re-get the prop details here in case changing another object-based option has resulted in
              // a change to this one due to a dependency
              Map<String, Object> propDetails = m_properties.get( propName );
              arrayValEditBut.setEnabled( false );
              Object arrValue = propDetails.get( "objectValue" );
              try {
                GAEDialog dialog = new GAEDialog( shell, SWT.OK | SWT.CANCEL, arrValue, (Class<?>) value, variables );
                dialog.open();
                Object newArrValue = dialog.getArray();
                propDetails.put( "objectValue", newArrValue );
                arrayElementType.setText( value.toString() + " : " + Array.getLength( newArrValue ) );
              } catch ( Exception ex ) {
                ex.printStackTrace();
              } finally {
                arrayValEditBut.setEnabled( true );
              }
            }
          } );
        }
      } else if ( type.equalsIgnoreCase( "pick-list" ) ) {
        String pickListValues = (String) propDetails.get( "pick-list-values" );
        String[] vals = pickListValues.split( "," );
        ComboVar pickListCombo = new ComboVar( variables, m_schemeGroup, SWT.BORDER | SWT.READ_ONLY );
        props.setLook( pickListCombo );
        for ( String v : vals ) {
          pickListCombo.add( v.trim() );
        }
        if ( value != null && value.toString().length() > 0 ) {
          pickListCombo.setText( value.toString() );
        }
        pickListCombo.addSelectionListener( new SelectionAdapter() {
          @Override public void widgetSelected( SelectionEvent selectionEvent ) {
            super.widgetSelected( selectionEvent );
            m_inputMeta.setChanged();
          }
        } );
        pickListCombo.setLayoutData( getFirstPromptFormData( propLabel ) );
        lastControl = pickListCombo;
        m_schemeWidgets.put( propName, pickListCombo );
      } else if ( type.equalsIgnoreCase( "boolean" ) ) {
        Button boolBut = new Button( m_schemeGroup, SWT.CHECK );
        props.setLook( boolBut );
        boolBut.setLayoutData( getFirstPromptFormData( propLabel ) );
        if ( value != null && value.toString().length() > 0 ) {
          boolBut.setSelection( Boolean.parseBoolean( value.toString() ) );
        }
        lastControl = boolBut;
        m_schemeWidgets.put( propName, boolBut );
            /* ComboVar pickListCombo = new ComboVar( transMeta, m_schemeGroup, SWT.BORDER | SWT.READ_ONLY );
            props.setLook( pickListCombo );
            pickListCombo.add( "true" );
            pickListCombo.add( "false" );
            if ( value != null && value.toString().length() > 0 ) {
              pickListCombo.setText( value.toString() );
            }
            pickListCombo.setLayoutData( getFirstPromptFormData( propLabel ) );
            lastControl = pickListCombo;
            m_schemeWidgets.put( propName, pickListCombo );
*/
      } else {
        Scrollable
            propVar =
            m_scheme.supportsEnvironmentVariables() ?
                new TextVar( variables, m_schemeGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER ) :
                new Text( m_schemeGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
        // Text propVar = new Text( m_schemeGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
        props.setLook( propVar );
        if ( value != null ) {
          if ( propVar instanceof Text ) {
            ( (Text) propVar ).setText( value.toString() );
          } else {
            ( (TextVar) propVar ).setText( value.toString() );
          }
        }
        if ( propVar instanceof Text ) {
          ( (Text) propVar ).addModifyListener( m_simpleModifyListener );
        } else {
          ( (TextVar) propVar ).addModifyListener( m_simpleModifyListener );
        }
        propVar.setLayoutData( getFirstPromptFormData( propLabel ) );
        lastControl = propVar;
        m_schemeWidgets.put( propName, propVar );
      }
    }

    // create a button for each category
    for ( String category : propGroupings ) {
      Label catLab = new Label( m_schemeGroup, SWT.RIGHT );
      props.setLook( catLab );
      catLab.setText( category );
      catLab.setLayoutData( getFirstLabelFormData() );

      Button catBut = new Button( m_schemeGroup, SWT.PUSH );
      props.setLook( catBut );
      catBut.setText( "Edit..." );
      catBut.setLayoutData( getFirstPromptFormData( catLab ) );
      catBut.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          catBut.setEnabled( false );
          try {
            GOEDialog dialog = new GOEDialog( shell, SWT.OK | SWT.CANCEL, m_scheme, m_topLevelSchemeInfo, variables );
            dialog.setPropertyGroupingCategory( category );
            dialog.open();
          } catch ( Exception ex ) {
            ex.printStackTrace();
          } finally {
            catBut.setEnabled( true );
          }
        }
      } );
      lastControl = catBut;
    }

    m_schemeGroup.layout();
    m_schemeComposite.layout();

    if ( m_scheme.supportsIncrementalTraining() ) {
      Label incrementalCacheLab = new Label( m_schemeComposite, SWT.RIGHT );
      props.setLook( incrementalCacheLab );
      incrementalCacheLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.Label" ) );
      incrementalCacheLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.TipText" ) );
      FormData fd = getFirstLabelFormData();
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      incrementalCacheLab.setLayoutData( fd );

      m_incrementalRowCacheField = new TextVar( variables, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
      props.setLook( m_incrementalRowCacheField );
      m_incrementalRowCacheField.addModifyListener( m_simpleModifyListener );
      fd = getFirstPromptFormData( incrementalCacheLab );
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      m_incrementalRowCacheField.setLayoutData( fd );

      m_incrementalRowCacheField.setText( m_inputMeta.getInitialRowCacheForNominalValDetermination() );
    }
  }

  @SuppressWarnings( "unchecked" )
  protected void populateSchemeTab( boolean engineChange, BaseSupervisedPMIMeta stepMeta ) {

    String currentEngine = m_engineDropDown.getText();
    if ( !org.apache.hop.core.util.Utils.isEmpty( currentEngine ) ) {
      try {
        PMIEngine eng = PMIEngine.getEngine( currentEngine );
        Scheme scheme = eng.getScheme( m_originalMeta.getSchemeName() );

        // Only configure with the current meta scheme options if the engine has not changed
        if ( !engineChange && !org.apache.hop.core.util.Utils.isEmpty( stepMeta.getSchemeCommandLineOptions() ) ) {
          scheme.setSchemeOptions( Utils.splitOptions( stepMeta.getSchemeCommandLineOptions() ) );
        }
        m_topLevelSchemeInfo = scheme.getSchemeInfo();
        m_scheme = scheme;

        buildPropertySheet();

        // lastControl = null;
      } catch ( Exception e ) {
        e.printStackTrace();
        ShowMessageDialog
            smd =
            new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
                BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemProcessingSchemeSettings.Title" ),
                e.getMessage(), false );
        smd.open();
      }
    }

    m_schemeGroup.layout();
    m_schemeComposite.layout();

    if ( m_scheme.supportsIncrementalTraining() ) {
      Label incrementalCacheLab = new Label( m_schemeComposite, SWT.RIGHT );
      props.setLook( incrementalCacheLab );
      incrementalCacheLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.Label" ) );
      incrementalCacheLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.TipText" ) );
      FormData fd = getFirstLabelFormData();
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      incrementalCacheLab.setLayoutData( fd );

      m_incrementalRowCacheField = new TextVar( variables, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
      props.setLook( m_incrementalRowCacheField );
      m_incrementalRowCacheField.addModifyListener( m_simpleModifyListener );
      fd = getFirstPromptFormData( incrementalCacheLab );
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      m_incrementalRowCacheField.setLayoutData( fd );

      m_incrementalRowCacheField.setText( m_inputMeta.getInitialRowCacheForNominalValDetermination() );
    }
  }

  protected void addPreprocessingTab() {
    m_preprocessingTab = new CTabItem( m_container, SWT.NONE );
    m_preprocessingTab.setText( BaseMessages.getString( PKG, "BasePMIStep.PreprocessingTab.Title" ) );

    m_preprocessingComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_preprocessingComposite );

    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_preprocessingComposite.setLayout( fl );

    try {
      m_samplingFilters = SchemeUtils.filterConfigsToList( m_originalMeta.getSamplingConfigs() );
      m_preprocessingFilters = SchemeUtils.filterConfigsToList( m_originalMeta.getPreprocessingConfigs() );

      // resample/class balance
      Label resampleLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( resampleLab );
      resampleLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.Label" ) );
      resampleLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.TipText" ) );
      lastControl = null;
      resampleLab.setLayoutData( getFirstLabelFormData() );

      m_resampleCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_resampleCheck );
      m_resampleCheck.setLayoutData( getFirstPromptFormData( resampleLab ) );
      m_resampleCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_resampleConfig.setEnabled( m_resampleCheck.getSelection() );
        }
      } );

      m_resampleConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      m_resampleConfig.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.Button" ) );
      props.setLook( m_resampleConfig );
      m_resampleConfig.setLayoutData( getSecondPromptFormData( m_resampleCheck ) );
      m_resampleConfig.setEnabled( false );
      lastControl = m_resampleCheck;

      m_resampleConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_resample, m_resampleConfig );
        }
      } );

      Label removeUselessLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( removeUselessLab );
      removeUselessLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.Label" ) );
      removeUselessLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.TipText" ) );
      removeUselessLab.setLayoutData( getFirstLabelFormData() );

      m_removeUselessCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_removeUselessCheck );
      m_removeUselessCheck.setLayoutData( getFirstPromptFormData( removeUselessLab ) );
      m_removeUselessCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_removeUselessConfig.setEnabled( m_removeUselessCheck.getSelection() );
        }
      } );

      m_removeUselessConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_removeUselessConfig );
      m_removeUselessConfig.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.Button" ) );
      m_removeUselessConfig.setEnabled( m_removeUselessCheck.getSelection() );
      m_removeUselessConfig.setLayoutData( getSecondPromptFormData( m_removeUselessCheck ) );
      lastControl = m_removeUselessCheck;

      m_removeUselessConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_removeUselessFilter, m_removeUselessConfig );
        }
      } );

      Label mergeInfequentLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( mergeInfequentLab );
      mergeInfequentLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.Label" ) );
      mergeInfequentLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.TipText" ) );
      mergeInfequentLab.setLayoutData( getFirstLabelFormData() );

      m_mergeInfrequentValsCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_mergeInfrequentValsCheck );
      m_mergeInfrequentValsCheck.setLayoutData( getFirstPromptFormData( mergeInfequentLab ) );
      m_mergeInfrequentValsCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_mergeInfrequentValsConfig.setEnabled( m_mergeInfrequentValsCheck.getSelection() );
        }
      } );

      m_mergeInfrequentValsConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_mergeInfrequentValsConfig );
      m_mergeInfrequentValsConfig
          .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.Button" ) );
      m_mergeInfrequentValsConfig.setEnabled( m_mergeInfrequentValsCheck.getSelection() );
      m_mergeInfrequentValsConfig.setLayoutData( getSecondPromptFormData( m_mergeInfrequentValsCheck ) );
      lastControl = m_mergeInfrequentValsCheck;

      m_mergeInfrequentValsConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_mergeInfrequentNominalValsFilter, m_mergeInfrequentValsConfig );
        }
      } );

      Label stringToWordVecLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( stringToWordVecLab );
      stringToWordVecLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.Label" ) );
      stringToWordVecLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.TipText" ) );
      stringToWordVecLab.setLayoutData( getFirstLabelFormData() );

      m_stringToWordVectorCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_stringToWordVectorCheck );
      m_stringToWordVectorCheck.setLayoutData( getFirstPromptFormData( stringToWordVecLab ) );
      m_stringToWordVectorCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_stringToWordVectorConfig.setEnabled( m_stringToWordVectorCheck.getSelection() );
        }
      } );

      m_stringToWordVectorConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_stringToWordVectorConfig );
      m_stringToWordVectorConfig
          .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.Button" ) );
      m_stringToWordVectorConfig.setEnabled( m_stringToWordVectorCheck.getSelection() );
      m_stringToWordVectorConfig.setLayoutData( getSecondPromptFormData( m_stringToWordVectorCheck ) );
      lastControl = m_stringToWordVectorConfig;

      m_stringToWordVectorConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_stringToWordVectorFilter, m_stringToWordVectorConfig );
        }
      } );

      m_resample = new Resample(); // assume a nominal class initially...
      m_removeUselessFilter = new RemoveUseless();
      m_mergeInfrequentNominalValsFilter = new MergeInfrequentNominalValues();
      m_mergeInfrequentNominalValsFilter.setAttributeIndices( "first-last" ); // default is 1,2
      m_stringToWordVectorFilter = new StringToWordVector();

    } catch ( Exception e ) {
      e.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              e.getMessage(), false );
      smd.open();
    }

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_preprocessingComposite.setLayoutData( fd );
    m_preprocessingComposite.layout();

    m_preprocessingTab.setControl( m_preprocessingComposite );
  }

  protected void popupEditorDialog( Object objectToEdit, Button button ) {
    try {
      button.setEnabled( false );
      GOEDialog dialog = new GOEDialog( getParent(), SWT.OK | SWT.CANCEL, objectToEdit, variables );
      dialog.open();
    } catch ( Exception ex ) {
      ex.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemEditingOptionsGOEDialog.Title" ),
              ex.getMessage(), false );
      smd.open();
    }
    button.setEnabled( true );
  }

  protected void setOptionsForPreprocessingFromMeta( BaseSupervisedPMIMeta meta ) throws Exception {
    Map<String, String> sampling = meta.getSamplingConfigs();

    for ( Map.Entry<String, String> e : sampling.entrySet() ) {
      if ( e.getKey().endsWith( ".Resample" ) ) {
        if ( e.getKey().contains( ".unsupervised" ) ) {
          // switch to unsupervised
          m_resample = new weka.filters.unsupervised.instance.Resample();
        }
      }
      /* // set options
      if ( !org.apache.hop.core.util.Utils.isEmpty( e.getValue() ) ) {
        Filter toSet = null;
        if ( e.getKey().endsWith( ".Resample" ) ) {
          toSet = m_resample;
        } else if ( e.getKey().endsWith( ".RemoveUseless" ) ) {
          toSet = m_removeUselessFilter;
        } else if ( e.getKey().endsWith( ".MergeInfrequentNominalValues" ) ) {
          toSet = m_mergeInfrequentNominalValsFilter;
        } else if ( e.getKey().endsWith( ".StringToWordVector" ) ) {
          toSet = m_stringToWordVectorFilter;
        }

        if ( toSet != null ) {
          toSet.setOptions( Utils.splitOptions( e.getValue() ) );
        }
      } */
    }

    setOptionsForFilter( m_resample, m_samplingFilters, m_resampleCheck, m_resampleConfig );
    setOptionsForFilter( m_removeUselessFilter, m_preprocessingFilters, m_removeUselessCheck, m_removeUselessConfig );
    setOptionsForFilter( m_mergeInfrequentNominalValsFilter, m_preprocessingFilters, m_mergeInfrequentValsCheck,
        m_mergeInfrequentValsConfig );
    setOptionsForFilter( m_stringToWordVectorFilter, m_preprocessingFilters, m_stringToWordVectorCheck,
        m_stringToWordVectorConfig );
  }

  protected void setOptionsForFilter( Filter filter, List<Filter> filterList, Button associatedCheckBox,
      Button associatedConfigBut ) {
    try {
      for ( Filter f : filterList ) {
        if ( f.getClass().getCanonicalName().equals( filter.getClass().getCanonicalName() ) ) {
          filter.setOptions( f.getOptions() );
          if ( associatedCheckBox != null ) {
            associatedCheckBox.setSelection( true );
            associatedConfigBut.setEnabled( true );
          }
          break;
        }
      }
    } catch ( Exception ex ) {
      ex.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              ex.getMessage(), false );
      smd.open();
    }
  }

  protected void addEvaluationTab() {
    m_evaluationTab = new CTabItem( m_container, SWT.NONE );
    m_evaluationTab.setText( BaseMessages.getString( PKG, "BasePMIStep.EvaluationTab.Title" ) );

    m_evaluationComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_evaluationComposite );

    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_evaluationComposite.setLayout( fl );

    Label evalModeLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    evalModeLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.EvaluationMode.Label" ) );
    props.setLook( evalModeLabel );
    lastControl = null;
    evalModeLabel.setLayoutData( getFirstLabelFormData() );

    m_evalModeDropDown = new ComboVar( variables, m_evaluationComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_evalModeDropDown );

    m_evalModeDropDown.setLayoutData( getFirstPromptFormData( evalModeLabel ) );
    lastControl = m_evalModeDropDown;
    m_evalModeDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        checkWidgets();
      }
    } );

    Label crossValLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    crossValLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.CrossValFolds.Label" ) );
    props.setLook( crossValLabel );
    crossValLabel.setLayoutData( getFirstLabelFormData() );
    crossValLabel.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.CrossValFolds.TipText" ) );

    m_xValFoldsField = new TextVar( variables, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_xValFoldsField );
    m_xValFoldsField.setLayoutData( getFirstPromptFormData( crossValLabel ) );
    lastControl = m_xValFoldsField;

    Label percentageSplitLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( percentageSplitLabel );
    percentageSplitLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.PercentageSplit.Label" ) );
    percentageSplitLabel.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.PercentageSplit.TipText" ) );
    percentageSplitLabel.setLayoutData( getFirstLabelFormData() );

    m_percentageSplitField = new TextVar( variables, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_percentageSplitField );
    m_percentageSplitField.setLayoutData( getFirstPromptFormData( percentageSplitLabel ) );
    lastControl = m_percentageSplitField;

    Label randomSeedLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( randomSeedLab );
    randomSeedLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeed.Label" ) );
    randomSeedLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeed.TipText" ) );
    randomSeedLab.setLayoutData( getFirstLabelFormData() );

    m_randomSeedField = new TextVar( variables, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_randomSeedField );
    m_randomSeedField.setLayoutData( getFirstPromptFormData( randomSeedLab ) );
    lastControl = m_randomSeedField;

    Label outputAUCLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( outputAUCLab );
    outputAUCLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.AUC.Label" ) );
    outputAUCLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.AUC.TipText" ) );
    outputAUCLab.setLayoutData( getFirstLabelFormData() );

    m_outputAUCMetricsCheck = new Button( m_evaluationComposite, SWT.CHECK );
    props.setLook( m_outputAUCMetricsCheck );
    m_outputAUCMetricsCheck.setLayoutData( getFirstPromptFormData( outputAUCLab ) );
    lastControl = m_outputAUCMetricsCheck;

    Label outputIRMetricsLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( outputIRMetricsLab );
    outputIRMetricsLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IR.Label" ) );
    outputIRMetricsLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.IR.TipText" ) );
    outputIRMetricsLab.setLayoutData( getFirstLabelFormData() );

    m_outputIRMetricsCheck = new Button( m_evaluationComposite, SWT.CHECK );
    props.setLook( m_outputAUCMetricsCheck );
    m_outputIRMetricsCheck.setLayoutData( getFirstPromptFormData( outputIRMetricsLab ) );

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_evaluationComposite.setLayoutData( fd );
    m_evaluationComposite.layout();

    m_evaluationTab.setControl( m_evaluationComposite );
  }

  protected void checkWidgets() {
    handleRowsToProcessChange();
    handleReservoirSamplingChange();

    wGet.setEnabled( !org.apache.hop.core.util.Utils.isEmpty( m_trainingStepDropDown.getText() ) );
    m_schemeGroup.setText(
        m_inputMeta.getSchemeName() + ( org.apache.hop.core.util.Utils.isEmpty( m_engineDropDown.getText() ) ? "" :
            " (" + m_engineDropDown.getText() + ")" ) );

    // enable/disable separate test drop-down based on evaluation mode selected
    String currentEvalSetting = m_evalModeDropDown.getText();
    boolean aucIREnable = checkAUCIRWidgets();
    if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.NONE.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( false );
      m_outputIRMetricsCheck.setEnabled( false );
      m_outputAUCMetricsCheck.setSelection( false );
      m_outputIRMetricsCheck.setSelection( false );
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.SEPARATE_TEST_SET.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( true );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.CROSS_VALIDATION.toString() ) ) {
      m_xValFoldsField.setEnabled( true );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( true );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.PERCENTAGE_SPLIT.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( true );
      m_randomSeedField.setEnabled( true );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    }

    // Check for IterableClassifier && evaluation mode
    if ( m_scheme.supportsResumableTraining() && m_rowsToProcessDropDown.getText().equalsIgnoreCase( "ALL" ) && (
        currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.NONE.toString() ) || currentEvalSetting
            .equalsIgnoreCase( Evaluator.EvalMode.SEPARATE_TEST_SET.toString() ) ) ) {
      if ( m_modelLoadField == null ) {
        m_modelLoadLab = new Label( m_schemeComposite, SWT.RIGHT );
        m_modelLoadLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IterativeModelLoad.Label" ) );
        props.setLook( m_modelLoadLab );
        lastControl = m_modelFilenameField;
        m_modelLoadLab.setLayoutData( getFirstLabelFormData() );

        m_modelLoadField = new TextVar( variables, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
        props.setLook( m_modelLoadField );
        m_modelLoadField.setLayoutData( getFirstPromptFormData( m_modelLoadLab ) );

        m_browseLoadModelButton = new Button( m_schemeComposite, SWT.PUSH );
        props.setLook( m_browseLoadModelButton );
        m_browseLoadModelButton
            .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.BrowseModelOutputDirectory.Button" ) );
        m_browseLoadModelButton.setLayoutData( getSecondLabelFormData( m_modelLoadField ) );

        m_browseLoadModelButton.addSelectionListener( new SelectionAdapter() {
          @Override public void widgetSelected( SelectionEvent selectionEvent ) {
            super.widgetSelected( selectionEvent );
            FileDialog dialog = new FileDialog( shell, SWT.OPEN );

            String modelPath = dialog.open();
            boolean ok = false;
            File updatedModelPath = null;
            if ( !org.apache.hop.core.util.Utils.isEmpty( modelPath ) && modelPath.toLowerCase()
                .startsWith( "file:" ) ) {
              modelPath = modelPath.replace( " ", "%20" );

              try {
                updatedModelPath = new File( new java.net.URI( modelPath ) );
                ok = true;
              } catch ( URISyntaxException e ) {
                e.printStackTrace();
              }
            } else {
              updatedModelPath = new File( modelPath );
              ok = true;
            }
            if ( ok && updatedModelPath.exists() && updatedModelPath.isFile() ) {
              if ( log != null ) {
                log.logBasic( "Loading/checking model: " + updatedModelPath.toString() );
                try {
                  List<Object> loaded = BaseSupervisedPMIData.loadModel( updatedModelPath.toString(), log );
                  m_modelLoadField.setText( updatedModelPath.toString() );

                  // Apply loaded model options to dialog
                  m_scheme.setConfiguredScheme( loaded.get( 0 ) );
                  m_topLevelSchemeInfo = m_scheme.getSchemeInfo();

                  buildPropertySheet();
                } catch ( Exception e ) {
                  // TODO popup error dialog
                  e.printStackTrace();
                }
              }
            }
          }
        } );

        m_schemeGroup.layout();
        m_schemeComposite.layout();
      }
    } else if ( m_modelLoadField != null ) {
      m_modelLoadLab.dispose();
      m_modelLoadField.dispose();
      m_browseLoadModelButton.dispose();
      m_modelLoadLab = null;
      m_modelLoadField = null;
      m_browseLoadModelButton = null;

      m_schemeGroup.layout();
      m_schemeComposite.layout();
    }
  }

  protected boolean checkAUCIRWidgets() {
    boolean enableCheckBoxes = false;
    if ( !org.apache.hop.core.util.Utils.isEmpty( m_classFieldDropDown.getText() ) ) {
      String classFieldName = variables.resolve( m_classFieldDropDown.getText() );

      int numNonEmpty = m_fieldsTable.nrNonEmpty();
      for ( int i = 0; i < numNonEmpty; i++ ) {
        TableItem item = m_fieldsTable.getNonEmpty( i );

        String fieldName = item.getText( 1 );
        if ( variables.resolve( fieldName ).equals( classFieldName ) ) {
          int arffType = getArffTypeInt( item.getText( 3 ) );
          if ( arffType == Attribute.NOMINAL ) {
            String nomVals = item.getText( 4 );
            enableCheckBoxes =
                !org.apache.hop.core.util.Utils
                    .isEmpty( nomVals ); // assume that this is a valid list of nominal labels
          }
          break;
        }
      }
    }

    return enableCheckBoxes;
  }

  protected void handleRowsToProcessChange() {
    // check and disable the size input if batch isn't selected
    String rowsToProcess = m_rowsToProcessDropDown.getText();
    if ( rowsToProcess.equals(
        BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.BatchEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( true );
      m_batchSizeField.setText( m_originalMeta.getBatchSize() );

      // reset other controllers
      m_reservoirSamplingBut.setEnabled( false );
      m_reservoirSamplingBut.setSelection( false );
      m_reservoirSizeField.setEnabled( false );
      m_reservoirSizeField.setText( "" );
      m_stratificationFieldDropDown.setEnabled( false );
      m_stratificationFieldDropDown.setText( "" );
    } else if ( rowsToProcess
        .equals( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.AllEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( false );
      m_batchSizeField.setText( "" );

      // reset the other controllers
      m_reservoirSamplingBut.setEnabled( true );
      m_reservoirSamplingBut.setSelection( m_originalMeta.getUseReservoirSampling() );
      m_reservoirSizeField.setEnabled( m_reservoirSamplingBut.getSelection() );
      m_reservoirSizeField.setText( m_originalMeta.getReservoirSize() );
      m_stratificationFieldDropDown.setEnabled( true );
      m_stratificationFieldDropDown.setText( m_originalMeta.getStratificationFieldName() );
    } else if ( rowsToProcess.equals(
        BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.StratifiedEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( false );
      m_reservoirSamplingBut.setEnabled( true );
      m_reservoirSamplingBut.setSelection( m_originalMeta.getUseReservoirSampling() );
      m_reservoirSizeField.setEnabled( true );
      m_reservoirSizeField.setText( m_originalMeta.getReservoirSize() );
      m_stratificationFieldDropDown.setEnabled( true );
      m_stratificationFieldDropDown.setText( m_originalMeta.getStratificationFieldName() );
    }
  }

  protected List<ArffMeta> getArffMetasForIncomingFields( boolean popupErrorDialogIfNecessary, boolean silent ) {

    try {
      IRowMeta row = getRowMetaForTrainingDataSourceStep();
      if ( row != null ) {
        return BaseSupervisedPMIData.fieldsToArffMetas( row );
      }
    } catch ( HopTransformException e ) {
      if ( popupErrorDialogIfNecessary ) {
        String message = BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnableToFindIncomingFields" );
        showMessageDialog( message, message, SWT.OK | SWT.ICON_WARNING, false );
      } else {
        if ( !silent ) {
          log.logDebug( BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnableToFindIncomingFields" ) );
        }
      }
    }

    return new ArrayList<>();
  }

  protected IRowMeta getRowMetaForTrainingDataSourceStep() throws HopTransformException {
    if ( org.apache.hop.core.util.Utils.isEmpty( m_trainingStepDropDown.getText() ) ) {
      return null;
    }
    // RowMetaInterface r = transMeta.getPrevStepFields( stepname );
    TransformMeta us = pipelineMeta.findTransform( transformName );
    List<TransformMeta> connected = pipelineMeta.findPreviousTransforms( us );
    TransformMeta trainingStep = null;
    for ( TransformMeta conn : connected ) {
      if ( conn.getName().equalsIgnoreCase( variables.resolve( m_trainingStepDropDown.getText() ) ) ) {
        trainingStep = conn;
        break;
      }
    }

    if ( trainingStep == null ) {
      // TODO popup warning/error
    }

    return pipelineMeta.getTransformFields( variables, us, null );
  }

  protected void populateClassAndStratCombos() {
    List<ArffMeta> incomingFields = getArffMetasForIncomingFields( false, true );
    String existingC = m_classFieldDropDown.getText();
    String existingS = m_stratificationFieldDropDown.getText();
    m_classFieldDropDown.removeAll();
    m_stratificationFieldDropDown.removeAll();
    for ( ArffMeta m : incomingFields ) {
      m_classFieldDropDown.add( m.getFieldName() );
      m_stratificationFieldDropDown.add( m.getFieldName() );
    }

    if ( !org.apache.hop.core.util.Utils.isEmpty( existingC ) ) {
      m_classFieldDropDown.setText( existingC );
    }
    if ( !org.apache.hop.core.util.Utils.isEmpty( existingS ) ) {
      m_stratificationFieldDropDown.setText( existingS );
    }
  }

  protected void setEvaluationModeFromMeta( BaseSupervisedPMIMeta meta ) {
    for ( Evaluator.EvalMode e : Evaluator.EvalMode.values() ) {
      String evalS = e.toString().toLowerCase();
      if ( !evalS.equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() )
          || m_scheme.supportsIncrementalTraining() && evalS
          .equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() ) ) {
        m_evalModeDropDown.add( evalS );
      }
    }

    Evaluator.EvalMode mode = meta.getEvalMode();
    if ( mode == null ) {
      mode = Evaluator.EvalMode.NONE;
    }

    m_evalModeDropDown.setText( mode.toString().toLowerCase() );
  }

  protected void populateFieldsTable() {
    try {

      IRowMeta r = getRowMetaForTrainingDataSourceStep();
      if ( r == null ) {
        return;
      }

      if ( r != null ) {
        BaseTransformDialog
            .getFieldsFromPrevious( r, m_fieldsTable, 1, new int[] { 1 }, new int[] { 2 }, -1, -1, null );

        // set some default arff stuff for the new fields
        int nrNonEmptyFields = m_fieldsTable.nrNonEmpty();
        for ( int i = 0; i < nrNonEmptyFields; i++ ) {
          TableItem item = m_fieldsTable.getNonEmpty( i );

          int hopType = ValueMetaFactory.getIdForValueMeta( item.getText( 2 ) );
          if ( org.apache.hop.core.util.Utils.isEmpty( item.getText( 3 ) ) ) {

            switch ( hopType ) {
              case IValueMeta.TYPE_NUMBER:
              case IValueMeta.TYPE_INTEGER:
              case IValueMeta.TYPE_BOOLEAN:
              case IValueMeta.TYPE_DATE:
                item.setText( 3, "Numeric" );
                break;
              case IValueMeta.TYPE_STRING: {
                item.setText( 3, "Nominal" );
                int index = r.indexOfValue( item.getText( 1 ) );
                IValueMeta vm = r.getValueMeta( index );
                if ( vm.getStorageType() == IValueMeta.STORAGE_TYPE_INDEXED ) {
                  Object[] legalValues = vm.getIndex();
                  String vals = "";
                  for ( int j = 0; i < legalValues.length; j++ ) {
                    if ( j != 0 ) {
                      vals += "," + legalValues[j].toString();
                    } else {
                      vals += legalValues[j].toString();
                    }
                  }
                  item.setText( 4, vals );
                }
              }
              break;
            }
          }
        }
      }
    } catch ( HopException e ) {
      logError( BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Message" ), e );
      new ErrorDialog( shell, BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Title" ),
          BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Message" ), e );
    }
  }

  protected void handleReservoirSamplingChange() {
    m_reservoirSizeField.setEnabled( m_reservoirSamplingBut.getSelection() );
  }

  protected void showMessageDialog( String title, String message, int flags, boolean scroll ) {
    ShowMessageDialog smd = new ShowMessageDialog( shell, flags, title, message, scroll );
    smd.open();
  }

  private FormData getFirstLabelFormData() {
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( FIRST_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  private FormData getFirstPromptFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.right = new FormAttachment( FIRST_PROMPT_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  private FormData getSecondLabelFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( SECOND_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  private FormData getSecondPromptFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.top = new FormAttachment( lastControl, MARGIN );
    fd.right = new FormAttachment( SECOND_PROMPT_RIGHT_PERCENTAGE, 0 );
    return fd;
  }

  private FormData getThirdPropmtFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( THIRD_PROMPT_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );

    return fd;
  }

  private FormData getFirstGOEFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.top = new FormAttachment( lastControl, MARGIN );
    fd.right = new FormAttachment( GOE_FIRST_BUTTON_RIGHT_PERCENTAGE, 0 );
    return fd;
  }

  private FormData getSecondGOEFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( GOE_SECOND_BUTTON_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );

    return fd;
  }

  protected void ok() {
    if ( org.apache.hop.core.util.Utils.isEmpty( wTransformName.getText() ) ) {
      return;
    }

    transformName = wTransformName.getText(); // return value

    setData( m_inputMeta );
    if ( !m_originalMeta.equals( m_inputMeta ) ) {
      m_inputMeta.setChanged();
      changed = m_inputMeta.hasChanged();
    }

    dispose();
  }

  protected void cancel() {
    transformName = null;
    m_inputMeta.setChanged( changed );
    dispose();
  }

}
