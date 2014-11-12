package gloderss.actions;

import emilia.entity.action.ActionAbstract;
import gloderss.Constants.Actions;
import java.util.HashMap;

public class DenounceExtortionAction extends ActionAbstract {
	
	public enum Param {
		ENTREPRENEUR_ID, MAFIOSO_ID;
	}
	
	
	/**
	 * DenounceExtortionAction constructor
	 * 
	 * @param entrepreneurId
	 *          Entrepreneur identification
	 * @param mafiosoId
	 *          Mafioso identification
	 * @return none
	 */
	public DenounceExtortionAction(int entrepreneurId, int mafiosoId) {
		super(Actions.DENOUNCE_EXTORTION.ordinal(), Actions.DENOUNCE_EXTORTION
				.name());
		
		this.params = new HashMap<Object, Object>();
		this.params.put(Param.ENTREPRENEUR_ID, entrepreneurId);
		this.params.put(Param.MAFIOSO_ID, mafiosoId);
	}
}