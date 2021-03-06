package gloderss.actions;

import java.util.HashMap;
import emilia.entity.action.ActionAbstract;
import gloderss.Constants.Actions;

public class ReleaseImprisonmentAction extends ActionAbstract {
  
  public enum Param {
    MAFIOSO_ID
  };
  
  
  /**
   * ReleaseImprisonmentAction constructor
   * 
   * @param mafiosoId
   *          Mafioso identification
   * @return none
   */
  public ReleaseImprisonmentAction( int mafiosoId ) {
    super( Actions.RELEASE_IMPRISONMENT.ordinal(),
        Actions.RELEASE_IMPRISONMENT.name() );
    
    this.params = new HashMap<Object, Object>();
    this.params.put( Param.MAFIOSO_ID, mafiosoId );
  }
}