package gloderss.agents.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import gloderss.Constants;
import gloderss.Constants.Norms;
import gloderss.actions.CollaborateAction;
import gloderss.actions.NormativeInfoSpreadAction;
import gloderss.actions.NotCollaborateAction;
import gloderss.actions.StateCompensationAction;
import gloderss.actions.CaptureMafiosoAction;
import gloderss.actions.CollaborationRequestAction;
import gloderss.actions.CustodyAction;
import gloderss.actions.DenounceExtortionAction;
import gloderss.actions.DenouncePunishmentAction;
import gloderss.actions.ImprisonmentAction;
import gloderss.actions.PentitiAction;
import gloderss.actions.ReleaseCustodyAction;
import gloderss.actions.ReleaseInvestigationAction;
import gloderss.actions.SpecificInvestigationAction;
import gloderss.actions.StatePunishmentAction;
import gloderss.agents.AbstractAgent;
import gloderss.agents.consumer.ConsumerAgent;
import gloderss.agents.entrepreneur.EntrepreneurAgent;
import gloderss.communication.InfoAbstract;
import gloderss.communication.InfoRequest;
import gloderss.communication.InfoSet;
import gloderss.communication.Message;
import gloderss.conf.StateConf;
import gloderss.engine.devs.EventSimulator;
import gloderss.engine.event.Event;
import gloderss.util.distribution.PDFAbstract;
import gloderss.util.random.RandomUtil;

public class StateOrg extends AbstractAgent implements IStateOrg {
	
	private StateConf													conf;
	
	private PDFAbstract												imprisonmentDurationPDF;
	
	private PDFAbstract												custodyPDF;
	
	private PDFAbstract												timeToCompensationPDF;
	
	private PDFAbstract												periodicityFondoPDF;
	
	private PDFAbstract												spreadInformationPDF;
	
	private double														fondoSolidarieta;
	
	private Map<Integer, PoliceOfficerAgent>	policeOfficers;
	
	private Map<Integer, ConsumerAgent>				consumers;
	
	private Map<Integer, EntrepreneurAgent>		entrepreneurs;
	
	private List<Integer>											mafiosiBlackList;
	
	private List<Integer>											investigateEntrepreneurs;
	
	private List<Integer>											allocatedPoliceOfficers;
	
	private Queue<CaptureMafiosoAction>				custodyQueue;
	
	private Queue<DenouncePunishmentAction>		assistQueue;
	
	
	/**
	 * State constructor
	 * 
	 * @param id
	 *          State identification
	 * @param conf
	 *          State configuration
	 * @param consumers
	 *          List of all consumers
	 * @param entrepreneurs
	 *          List of all entrepreneurs
	 * @return none
	 */
	public StateOrg(Integer id, EventSimulator simulator, StateConf conf,
			Map<Integer, ConsumerAgent> consumers,
			Map<Integer, EntrepreneurAgent> entrepreneurs) {
		super(id, simulator);
		
		this.conf = conf;
		this.imprisonmentDurationPDF = PDFAbstract.getInstance(conf
				.getImprisonmentDurationPDF());
		
		this.custodyPDF = PDFAbstract.getInstance(conf.getCustodyDurationPDF());
		
		this.timeToCompensationPDF = PDFAbstract.getInstance(conf
				.getTimeToCompensationPDF());
		
		this.periodicityFondoPDF = PDFAbstract.getInstance(conf
				.getPeriodicityFondoPDF());
		
		this.spreadInformationPDF = PDFAbstract.getInstance(conf
				.getInformationSpreadPDF());
		
		this.fondoSolidarieta = 0.0;
		
		this.consumers = consumers;
		
		// Make Entrepreneur recognize the State identification
		this.entrepreneurs = entrepreneurs;
		for(Integer entrepreneurId : this.entrepreneurs.keySet()) {
			InfoSet infoSet = new InfoSet(id, entrepreneurId,
					Constants.PARAMETER_STATE_ID, id);
			this.sendInfo(infoSet);
		}
		
		PoliceOfficerAgent police;
		int policeId = id + 1;
		this.policeOfficers = new HashMap<Integer, PoliceOfficerAgent>();
		for(int i = 0; i < this.conf.getNumberPoliceOfficers(); i++, policeId++) {
			police = new PoliceOfficerAgent(policeId, simulator, this.conf, this.id);
			this.policeOfficers.put(policeId, police);
		}
		
		this.mafiosiBlackList = new ArrayList<Integer>();
		this.investigateEntrepreneurs = new ArrayList<Integer>();
		this.allocatedPoliceOfficers = new ArrayList<Integer>();
		this.custodyQueue = new LinkedList<CaptureMafiosoAction>();
		this.assistQueue = new LinkedList<DenouncePunishmentAction>();
	}
	
	
	/*******************************
	 * 
	 * Getters and Setters
	 * 
	 *******************************/
	
	public StateConf getConf() {
		return this.conf;
	}
	
	
	public Map<Integer, PoliceOfficerAgent> getPoliceOfficers() {
		return this.policeOfficers;
	}
	
	
	/*******************************
	 * 
	 * Decision Processes
	 * 
	 *******************************/
	
	/**
	 * Selects an Entrepreneur for investigation
	 * 
	 * @param none
	 * @return Entrepreneur for investigation
	 */
	public int decideEntrepreneur() {
		
		int entrepreneurId;
		do {
			
			entrepreneurId = (int) this.entrepreneurs.keySet().toArray()[RandomUtil
					.nextIntFromTo(0, (this.entrepreneurs.size() - 1))];
			
		} while(this.investigateEntrepreneurs.contains(entrepreneurId));
		
		return entrepreneurId;
	}
	
	
	/**
	 * Increase the Fondo di Solidarieta by a constant amount
	 * 
	 * @param none
	 * @return none
	 */
	public void increaseFondo() {
		
		this.fondoSolidarieta += this.conf.getResourceFondo();
		
		Event event = new Event(this.simulator.now()
				+ this.periodicityFondoPDF.nextValue(), this,
				Constants.EVENT_RESOURCE_FONDO);
		this.simulator.insert(event);
	}
	
	
	/**
	 * Judge whether a captured Mafioso should be imprisoned
	 * 
	 * @param none
	 * @return none
	 */
	public void judgeMafioso() {
		
		if(!this.custodyQueue.isEmpty()) {
			CaptureMafiosoAction action = this.custodyQueue.poll();
			this.decideImprisonment(action);
		}
	}
	
	
	@Override
	public void initializeSim() {
		for(PoliceOfficerAgent police : this.policeOfficers.values()) {
			police.initializeSim();
		}
		
		Event event = new Event(this.simulator.now() + 1, this,
				Constants.EVENT_RESOURCE_FONDO);
		this.simulator.insert(event);
		
		event = new Event(this.simulator.now()
				+ this.spreadInformationPDF.nextValue(), this,
				Constants.EVENT_SPREAD_INFORMATION);
		this.simulator.insert(event);
	}
	
	
	@Override
	public void decideInvestigateExtortion(DenounceExtortionAction action) {
		
		int entrepreneurId = (int) action
				.getParam(DenounceExtortionAction.Param.ENTREPRENEUR_ID);
		
		if((!this.investigateEntrepreneurs.contains(entrepreneurId))
				&& (RandomUtil.nextDouble() < this.conf.getInvestigateProbability())) {
			
			int mafiosoId = (int) action
					.getParam(DenounceExtortionAction.Param.MAFIOSO_ID);
			
			if(!this.mafiosiBlackList.contains(mafiosoId)) {
				this.mafiosiBlackList.add(mafiosoId);
				
				for(PoliceOfficerAgent police : this.policeOfficers.values()) {
					InfoSet set = new InfoSet(this.id, police.getId(),
							Constants.PARAMETER_ADD_MAFIOSO, mafiosoId);
					this.sendInfo(set);
				}
			}
			
			int policeOfficerId;
			do {
				
				policeOfficerId = (int) this.policeOfficers.keySet().toArray()[RandomUtil
						.nextIntFromTo(0, (this.policeOfficers.size() - 1))];
				
			} while(this.allocatedPoliceOfficers.contains(policeOfficerId));
			
			SpecificInvestigationAction investigation = new SpecificInvestigationAction(
					policeOfficerId, entrepreneurId);
			
			Message msg = new Message(this.simulator.now(), this.id, policeOfficerId,
					investigation);
			this.sendMsg(msg);
		}
	}
	
	
	@Override
	public void decideInvestigatePunishment(DenouncePunishmentAction action) {
		
		int entrepreneurId = (int) action
				.getParam(DenouncePunishmentAction.Param.ENTREPRENEUR_ID);
		
		if(!this.investigateEntrepreneurs.contains(entrepreneurId)) {
			
			int mafiosoId = (int) action
					.getParam(DenouncePunishmentAction.Param.MAFIOSO_ID);
			
			if(!this.mafiosiBlackList.contains(mafiosoId)) {
				this.mafiosiBlackList.add(mafiosoId);
				
				for(PoliceOfficerAgent police : this.policeOfficers.values()) {
					InfoSet set = new InfoSet(this.id, police.getId(),
							Constants.PARAMETER_ADD_MAFIOSO, mafiosoId);
					this.sendInfo(set);
				}
			}
			
			int policeOfficerId;
			do {
				
				policeOfficerId = (int) this.policeOfficers.keySet().toArray()[RandomUtil
						.nextIntFromTo(0, (this.policeOfficers.size() - 1))];
				
			} while(this.allocatedPoliceOfficers.contains(policeOfficerId));
			
			SpecificInvestigationAction investigation = new SpecificInvestigationAction(
					policeOfficerId, entrepreneurId);
			
			Message msg = new Message(this.simulator.now(), this.id, policeOfficerId,
					investigation);
			this.sendMsg(msg);
		}
		
		// Add Entrepreneur in a queue to receive compensation for the punishment
		this.assistQueue.add(action);
		
		Event event = new Event(this.simulator.now()
				+ this.timeToCompensationPDF.nextValue(), this,
				Constants.EVENT_ASSIST_ENTREPRENEUR);
		this.simulator.insert(event);
	}
	
	
	@Override
	public void releaseInvestigation(ReleaseInvestigationAction action) {
		
		int policeOfficerId = (int) action
				.getParam(ReleaseInvestigationAction.Param.POLICE_OFFICER_ID);
		
		if(this.allocatedPoliceOfficers.contains(policeOfficerId)) {
			this.allocatedPoliceOfficers.remove(policeOfficerId);
		}
	}
	
	
	@Override
	public void decideCustody(CaptureMafiosoAction action) {
		
		int mafiosoId = (int) action
				.getParam(CaptureMafiosoAction.Param.MAFIOSO_ID);
		
		CustodyAction custody = new CustodyAction(mafiosoId,
				this.custodyPDF.nextValue());
		
		Message msg = new Message(this.simulator.now(), this.id, mafiosoId, custody);
		this.sendMsg(msg);
		
		Event event = new Event(this.simulator.now() + this.custodyPDF.nextValue(),
				this, Constants.EVENT_JUDGE_MAFIOSO);
		this.simulator.insert(event);
		
		this.custodyQueue.add(action);
	}
	
	
	@Override
	public void decideImprisonment(CaptureMafiosoAction action) {
		
		int mafiosoId = (int) action
				.getParam(CaptureMafiosoAction.Param.MAFIOSO_ID);
		
		// Decide whether the Mafioso is going to be imprisoned
		if(RandomUtil.nextDouble() < this.conf.getImprisonmentProbability()) {
			
			InfoRequest wealthRequest = new InfoRequest(this.id, mafiosoId,
					Constants.REQUEST_WEALTH);
			double wealth = (double) this.sendInfo(wealthRequest);
			
			this.fondoSolidarieta += wealth * this.conf.getProportionTransferFondo();
			
			InfoSet wealthSet = new InfoSet(this.id, mafiosoId,
					Constants.PARAMETER_WEALTH, 0.0);
			this.sendInfo(wealthSet);
			
			double duration = this.imprisonmentDurationPDF.nextValue();
			
			ImprisonmentAction imprisonment = new ImprisonmentAction(mafiosoId,
					duration);
			
			Message msg = new Message(this.simulator.now(), this.id, mafiosoId,
					imprisonment);
			this.sendMsg(msg);
			
			// Release the Mafioso of custody
		} else {
			
			ReleaseCustodyAction releaseCustody = new ReleaseCustodyAction();
			
			Message msg = new Message(this.simulator.now(), this.id, mafiosoId,
					releaseCustody);
			this.sendMsg(msg);
		}
	}
	
	
	@Override
	public void receivePentiti(PentitiAction action) {
		
		@SuppressWarnings("unchecked")
		List<Integer> mafiosoList = (List<Integer>) action
				.getParam(PentitiAction.Param.MAFIOSI_LIST);
		
		if(mafiosoList != null) {
			for(Integer mafiosoId : mafiosoList) {
				if(!this.mafiosiBlackList.contains(mafiosoId)) {
					this.mafiosiBlackList.add(mafiosoId);
				}
			}
		}
		
		int mafiosoId = (int) action.getParam(PentitiAction.Param.MAFIOSO_ID);
		
		@SuppressWarnings("unchecked")
		List<Integer> entrepreneurList = (List<Integer>) action
				.getParam(PentitiAction.Param.ENTREPRENEUR_LIST);
		
		if(entrepreneurList != null) {
			for(Integer entrepreneurId : entrepreneurList) {
				CollaborationRequestAction collaboration = new CollaborationRequestAction(
						mafiosoId, entrepreneurId);
				
				Message msg = new Message(this.simulator.now(), this.id,
						entrepreneurId, collaboration);
				this.sendMsg(msg);
			}
		}
	}
	
	
	@Override
	public void receiveCollaboration(CollaborateAction action) {
		// Purposefully left blank
	}
	
	
	@Override
	public void decideStatePunishment(NotCollaborateAction action) {
		
		if(RandomUtil.nextDouble() < this.conf
				.getNoCollaborationPunishmentProbability()) {
			int entrepreneurId = (int) action
					.getParam(NotCollaborateAction.Param.ENTREPRENEUR_ID);
			
			if(this.assistQueue.contains(entrepreneurId)) {
				this.assistQueue.remove(entrepreneurId);
			}
			
			StatePunishmentAction punishment = new StatePunishmentAction(
					entrepreneurId, this.conf.getNoCollaborationPunishment());
			
			Message msg = new Message(this.simulator.now(), this.id, entrepreneurId,
					punishment);
			this.sendMsg(msg);
		}
	}
	
	
	@Override
	public void decideStateCompensation() {
		
		if(!this.assistQueue.isEmpty()) {
			DenouncePunishmentAction action = this.assistQueue.poll();
			
			double punishment = (double) action
					.getParam(DenouncePunishmentAction.Param.PUNISHMENT);
			
			// State has enough money to compensate the Entrepreneur
			if(this.fondoSolidarieta >= punishment) {
				int entrepreneurId = (int) action
						.getParam(DenouncePunishmentAction.Param.ENTREPRENEUR_ID);
				
				StateCompensationAction compensation = new StateCompensationAction(
						entrepreneurId, punishment);
				
				Message msg = new Message(this.simulator.now(), this.id,
						entrepreneurId, compensation);
				this.sendMsg(msg);
				
				// Entrepreneur goes back to the queue
			} else {
				
				this.assistQueue.add(action);
				
				Event event = new Event(this.simulator.now()
						+ this.timeToCompensationPDF.nextValue(), this,
						Constants.EVENT_ASSIST_ENTREPRENEUR);
				this.simulator.insert(event);
				
			}
		}
	}
	
	
	@Override
	public void spreadInformation() {
		
		// Spread information to Consumers
		NormativeInfoSpreadAction notBuyPayExtortion = new NormativeInfoSpreadAction(
				this.id, Norms.BUY_FROM_NOT_PAYING_ENTREPRENEURS.name());
		
		int numConsumers = (int) (this.consumers.size() * this.conf
				.getProportionCustomers());
		List<Integer> consumerIds = new ArrayList<Integer>();
		while(consumerIds.size() < numConsumers) {
			
			int consumerId = (int) this.consumers.keySet().toArray()[RandomUtil
					.nextIntFromTo(0, (this.consumers.size() - 1))];
			
			if(!consumerIds.contains(consumerId)) {
				consumerIds.add(consumerId);
				
				Message msg = new Message(this.simulator.now(), this.id, consumerId,
						notBuyPayExtortion);
				this.sendMsg(msg);
			}
		}
		
		NormativeInfoSpreadAction notPayExtortion = new NormativeInfoSpreadAction(
				this.id, Norms.NOT_PAY_EXTORTION.name());
		
		NormativeInfoSpreadAction denounceExtortion = new NormativeInfoSpreadAction(
				this.id, Norms.DENOUNCE_EXTORTION.name());
		
		int numEntrepreneurs = (int) (this.entrepreneurs.size() * this.conf
				.getProportionEntrepreneurs());
		List<Integer> entrepreneurIds = new ArrayList<Integer>();
		int qtyEntrepreneurs = this.entrepreneurs.size();
		while(entrepreneurIds.size() < numEntrepreneurs) {
			
			int entrepreneurId = (int) this.entrepreneurs.keySet().toArray()[RandomUtil
					.nextIntFromTo(0, (qtyEntrepreneurs - 1))];
			
			if(!entrepreneurIds.contains(entrepreneurId)) {
				entrepreneurIds.add(entrepreneurId);
				
				Message msgNP = new Message(this.simulator.now(), this.id,
						entrepreneurId, notPayExtortion);
				this.sendMsg(msgNP);
				
				Message msgD = new Message(this.simulator.now(), this.id,
						entrepreneurId, denounceExtortion);
				this.sendMsg(msgD);
			}
		}
		
		Event event = new Event(this.simulator.now()
				+ this.spreadInformationPDF.nextValue(), this,
				Constants.EVENT_SPREAD_INFORMATION);
		this.simulator.insert(event);
	}
	
	
	/*******************************
	 * 
	 * Handle communication requests
	 * 
	 *******************************/
	
	@Override
	public synchronized void handleMessage(Message msg) {
		
		Object content = msg.getContent();
		
		if((msg.getSender() != this.id) && (msg.getReceiver().contains(this.id))) {
			
			if(content instanceof DenounceExtortionAction) {
				this.decideInvestigateExtortion((DenounceExtortionAction) content);
				
			} else if(content instanceof DenouncePunishmentAction) {
				this.decideInvestigatePunishment((DenouncePunishmentAction) content);
				
			} else if(content instanceof PentitiAction) {
				this.receivePentiti((PentitiAction) content);
				
			} else if(content instanceof ReleaseInvestigationAction) {
				this.releaseInvestigation((ReleaseInvestigationAction) content);
				
			} else if(content instanceof CaptureMafiosoAction) {
				this.decideImprisonment((CaptureMafiosoAction) content);
				
			}
		}
	}
	
	
	@Override
	public Object handleInfo(InfoAbstract info) {
		Object infoRequested = null;
		
		if(info.getType().equals(InfoAbstract.Type.REQUEST)) {
			
			InfoRequest request = (InfoRequest) info;
			switch(request.getInfoRequest()) {
				case Constants.REQUEST_ID:
					infoRequested = this.id;
					break;
				case Constants.REQUEST_ENTREPRENEUR_ID:
					infoRequested = this.decideEntrepreneur();
					break;
			}
			
		} else if(info.getType().equals(InfoAbstract.Type.SET)) {
			
		}
		
		return infoRequested;
	}
	
	
	@Override
	public void handleObservation(Message msg) {
		// NOTHING
	}
	
	
	/*******************************
	 * 
	 * Handle simulation events
	 * 
	 *******************************/
	
	@Override
	public void handleEvent(Event event) {
		
		switch((String) event.getCommand()) {
			case Constants.EVENT_RESOURCE_FONDO:
				this.increaseFondo();
				break;
			case Constants.EVENT_RELEASE_CUSTODY:
				this.judgeMafioso();
				break;
			case Constants.EVENT_ASSIST_ENTREPRENEUR:
				this.decideStateCompensation();
				break;
			case Constants.EVENT_SPREAD_INFORMATION:
				this.spreadInformation();
		}
	}
}