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

import weka.clusterers.Clusterer;
import weka.clusterers.DensityBasedClusterer;
import weka.clusterers.UpdateableClusterer;
import weka.core.BatchPredictor;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.filters.unsupervised.attribute.Remove;

/**
 * Subclass of PMIScoringModel that encapsulates a Clusterer.
 *
 * @author Mark Hall (mhall{[at]}waikato{[dot]}ac{[dot]}nz)
 */
public class PMIScoringClusterer extends PMIScoringModel {

  // The encapsulated clusterer
  private Clusterer m_model;

  // Any attributes to ignore
  private Remove m_ignoredAtts;

  private String m_ignoredString;

  /**
   * Creates a new <code>PMIScoringClusterer</code> instance.
   *
   * @param model the Clusterer
   */
  public PMIScoringClusterer( Object model ) {
    super( model );
  }

  /**
   * Sets up a Remove filter to remove attributes that
   * are to be ignored by the clusterer. setHeader must
   * be called before this method.
   *
   * @param attsToIgnore any attributes to ignore during the scoring process
   */
  public void setAttributesToIgnore( int[] attsToIgnore ) throws Exception {
    Instances headerI = getHeader();
    m_ignoredAtts = new Remove();
    m_ignoredAtts.setAttributeIndicesArray( attsToIgnore );
    m_ignoredAtts.setInvertSelection( false );
    m_ignoredAtts.setInputFormat( headerI );

    StringBuilder temp = new StringBuilder();
    temp.append( "Attributes ignored by clusterer:\n\n" );
    for ( int i = 0; i < attsToIgnore.length; i++ ) {
      temp.append( headerI.attribute( attsToIgnore[i] ).name() ).append( "\n" );
    }
    temp.append( "\n\n" );
    m_ignoredString = temp.toString();
  }

  /**
   * Set the Clusterer model
   *
   * @param model a Clusterer
   */
  public void setModel( Object model ) {
    m_model = (Clusterer) model;
  }

  /**
   * Get the Clusterer model
   *
   * @return the Weka model as an object
   */
  public Object getModel() {
    return m_model;
  }

  /**
   * Return a classification (cluster that the test instance
   * belongs to)
   *
   * @param inst the Instance to be clustered (predicted)
   * @return the cluster number
   * @throws Exception if an error occurs
   */
  public double classifyInstance( Instance inst ) throws Exception {
    if ( m_ignoredAtts != null ) {
      inst = applyFilter( inst );
    }
    return (double) m_model.clusterInstance( inst );
  }

  /**
   * Update (if possible) the model with the supplied instance
   *
   * @param inst the Instance to update with
   * @return true if the update was updated successfully
   * @throws Exception if an error occurs
   */
  public boolean update( Instance inst ) throws Exception {
    // Only cobweb is updateable at present
    if ( isUpdateableModel() ) {
      if ( m_ignoredAtts != null ) {
        inst = applyFilter( inst );
      }
      //      System.err.println("In update...");
      ( (UpdateableClusterer) m_model ).updateClusterer( inst );
      //      System.err.println(m_model);
      return true;
    }
    return false;
  }

  /**
   * Return a probability distribution (over clusters).
   *
   * @param inst the Instance to be predicted
   * @return a probability distribution
   * @throws Exception if an error occurs
   */
  public double[] distributionForInstance( Instance inst ) throws Exception {
    if ( m_ignoredAtts != null ) {
      inst = applyFilter( inst );
    }
    return m_model.distributionForInstance( inst );
  }

  private Instance applyFilter( Instance inputInstance ) throws Exception {
    if ( !m_ignoredAtts.input( inputInstance ) ) {
      throw new Exception( "[WekaScoring] Filter didn't make the test instance" + " immediately available!" );
    }
    m_ignoredAtts.batchFinished();
    return m_ignoredAtts.output();
  }

  /**
   * Returns false. Clusterers are unsupervised methods.
   *
   * @return false
   */
  public boolean isSupervisedLearningModel() {
    return false;
  }

  /**
   * Return true if the underlying clusterer is incremental
   *
   * @return true if the underlying clusterer is incremental
   */
  public boolean isUpdateableModel() {
    return m_model instanceof UpdateableClusterer;
  }

  /**
   * Returns true if the wrapped clusterer can produce
   * cluster membership probability estimates
   *
   * @return true if probability estimates can be produced
   */
  public boolean canProduceProbabilities() {
    return m_model instanceof DensityBasedClusterer;
  }

  /**
   * Returns the number of clusters that the encapsulated
   * Clusterer has learned.
   *
   * @return the number of clusters in the model.
   * @throws Exception if an error occurs
   */
  public int numberOfClusters() throws Exception {
    return m_model.numberOfClusters();
  }

  /**
   * Returns the textual description of the Clusterer's model.
   *
   * @return the Clusterer's model as a String
   */
  public String toString() {
    String ignored = ( m_ignoredString == null ) ? "" : m_ignoredString;

    return ignored + m_model.toString();
  }

  /**
   * Batch scoring method.
   *
   * @param insts the instances to score
   * @return an array of predictions (index of the predicted class label for
   * each instance)
   * @throws Exception if a problem occurs
   */
  public double[] classifyInstances( Instances insts ) throws Exception {
    double[][] preds = distributionsForInstances( insts );

    double[] result = new double[preds.length];
    for ( int i = 0; i < preds.length; i++ ) {
      double[] p = preds[i];

      if ( Utils.sum( p ) <= 0 ) {
        result[i] = Utils.missingValue();
      } else {
        result[i] = Utils.maxIndex( p );
      }
    }

    return result;
  }

  /**
   * Batch scoring method
   *
   * @param insts the instances to get predictions for
   * @return an array of probability distributions, one for each instance
   * @throws Exception if a problem occurs
   */
  public double[][] distributionsForInstances( Instances insts ) throws Exception {
    if ( !isBatchPredictor() ) {
      throw new Exception( "Weka model cannot produce batch predictions!" );
    }

    return ( (BatchPredictor) m_model ).distributionsForInstances( insts );
  }

  /**
   * Returns true if the encapsulated Weka model can produce
   * predictions in a batch.
   *
   * @return true if the encapsulated Weka model can produce
   * predictions in a batch
   */
  public boolean isBatchPredictor() {
    return ( m_model instanceof BatchPredictor );
  }
}
