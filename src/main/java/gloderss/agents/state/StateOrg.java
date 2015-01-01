package gloderss.agents.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sourceforge.jeval.EvaluationException;
import net.sourceforge.jeval.Evaluator;
import gloderss.Constants;
import gloderss.Constants.Norms;
import gloderss.actions.CollaborateAction;
import gloderss.actions.NormativeInfoAction;
import gloderss.actions.NotCollaborateAction;
import gloderss.actions.ReleaseImprisonmentAction;
import gloderss.actions.StateCompensationAction;
import gloderss.actions.CaptureMafiosoAction;
import gloderss.actions.CollaborationRequestAction;
import gloderss.actions.CustodyAction;
import gloderss.actions.DenounceExtortionAction;
import gloderss.actions.DenouncePunishmentAction;
import gloderss.actions.ImprisonmentAction;
import gloderss.actions.PentitoAction;
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
import gloderss.output.AbstractEntity;
import gloderss.output.ExtortionOutputEntity;
import gloderss.output.CompensationOutputEntity;
import gloderss.output.NormativeOutputEntity;
import gloderss.output.OutputController;
import gloderss.output.AbstractEntity.EntityType;
import gloderss.output.StateOutputEntity;
import gloderss.util.distribution.PDFAbstract;
import gloderss.util.random.RandomUtil;

public class StateOrg extends AbstractAgent implements IStateOrg {
	
	private final static Logger								logger				= LoggerFactory
																															.getLogger(StateOrg.class);
	
	private static final String								COLLABORATION	= "COLLABORATION";
	
	private StateConf													conf;
	
	private int																ioId;
	
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
	
	private Queue<ImprisonmentAction>					prisonQueue;
	
	private Queue<DenouncePunishmentAction>		assistQueue;
	
	private Map<Integer, Integer>							collaborations;
	
	
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
			
			infoSet = new InfoSet(id, entrepreneurId,
					Constants.PARAMETER_STATE_PUNISHMENT,
					conf.getNoCollaborationPunishment());
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
		this.prisonQueue = new LinkedList<ImprisonmentAction>();
		this.assistQueue = new LinkedList<DenouncePunishmentAction>();
		this.collaborations = new HashMap<Integer, Integer>();
	}
	
	
	/*******************************
	 * 
	 * Getters and Setters
	 * 
	 *******************************/
	
	public int getIOId() {
		return this.ioId;
	}
	
	
	public void setIOId(int ioId) {
		this.ioId = ioId;
	}
	
	
	public Map<Integer, PoliceOfficerAgent> getPoliceOfficers() {
		return this.policeOfficers;
	}
	
	
	/*******************************
	 * 
	 * Decision Processes
	 * 
	 *******************************/
	
	@Override
	public void initializeSim() {
		for(PoliceOfficerAgent police : this.policeOfficers.values()) {
			police.initializeSim();
		}
		
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				AbstractEntity.EntityType.STATE);
		outputEntity.setValue(StateOutputEntity.Field.TIME.name(),
				this.simulator.now());
		outputEntity.setValue(StateOutputEntity.Field.FONDO.name(), 0.0);
		outputEntity.setActive();
		
		Event event = new Event(this.simulator.now(), this,
				Constants.EVENT_RESOURCE_FONDO);
		this.simulator.insert(event);
		
		event = new Event(this.simulator.now()
				+ this.spreadInformationPDF.nextValue(), this,
				Constants.EVENT_SPREAD_INFORMATION);
		this.simulator.insert(event);
	}
	
	
	@Override
	public void decideInvestigateExtortion(DenounceExtortionAction action) {
		
		int extortionId = (int) action
				.getParam(DenounceExtortionAction.Param.EXTORTION_ID);
		
		int entrepreneurId = (int) action
				.getParam(DenounceExtortionAction.Param.ENTREPRENEUR_ID);
		
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				EntityType.EXTORTION, extortionId);
		
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
					extortionId, policeOfficerId, entrepreneurId);
			
			Message msg = new Message(this.simulator.now(), this.id, policeOfficerId,
					investigation);
			this.sendMsg(msg);
			
			// Output
			outputEntity.setValue(
					ExtortionOutputEntity.Field.INVESTIGATED_EXTORTION.name(), true);
			
		} else {
			
			outputEntity.setValue(
					ExtortionOutputEntity.Field.INVESTIGATED_EXTORTION.name(), false);
			outputEntity.setActive();
		}
	}
	
	
	@Override
	public void decideInvestigatePunishment(DenouncePunishmentAction action) {
		
		int extortionId = (int) action
				.getParam(DenouncePunishmentAction.Param.EXTORTION_ID);
		
		int entrepreneurId = (int) action
				.getParam(DenouncePunishmentAction.Param.ENTREPRENEUR_ID);
		
		int mafiosoId = (int) action
				.getParam(DenouncePunishmentAction.Param.MAFIOSO_ID);
		
		if(!this.investigateEntrepreneurs.contains(entrepreneurId)) {
			
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
					extortionId, policeOfficerId, entrepreneurId);
			
			Message msg = new Message(this.simulator.now(), this.id, policeOfficerId,
					investigation);
			this.sendMsg(msg);
		}
		
		// Add Entrepreneur in a queue to receive compensation for the punishment
		this.assistQueue.add(action);
		
		// Spread action Denounce Punishment
		Message msg = new Message(this.simulator.now(), entrepreneurId, this.id,
				action);
		this.spreadActionInformation(msg);
		
		// Output
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				EntityType.EXTORTION, extortionId);
		outputEntity.setValue(
				ExtortionOutputEntity.Field.INVESTIGATED_PUNISHMENT.name(), true);
		
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
		
		int extortionId = (int) action
				.getParam(CaptureMafiosoAction.Param.EXTORTION_ID);
		
		int mafiosoId = (int) action
				.getParam(CaptureMafiosoAction.Param.MAFIOSO_ID);
		
		CustodyAction custody = new CustodyAction(extortionId, this.id, mafiosoId,
				this.custodyPDF.nextValue());
		
		Message msg = new Message(this.simulator.now(), this.id, mafiosoId, custody);
		this.sendMsg(msg);
		
		// Spread action
		this.spreadActionInformation(msg);
		
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				EntityType.EXTORTION, extortionId);
		
		// Collaboration evidence collection
		if(RandomUtil.nextDouble() < this.conf.getEvidenceProbability()) {
			
			InfoRequest info = new InfoRequest(this.id, mafiosoId,
					Constants.REQUEST_COLLECT_PAYERS);
			
			@SuppressWarnings("unchecked")
			List<Integer> evidences = (List<Integer>) this.sendInfo(info);
			if(!evidences.isEmpty()) {
				for(Integer entrepreneurId : evidences) {
					CollaborationRequestAction collaboration = new CollaborationRequestAction(
							mafiosoId, entrepreneurId);
					
					msg = new Message(this.simulator.now(), this.id, entrepreneurId,
							collaboration);
					this.sendMsg(msg);
				}
			}
			
			outputEntity.setValue(
					ExtortionOutputEntity.Field.EVIDENCE_COLLECTED.name(),
					evidences.size());
		}
		
		// Output
		if(extortionId >= 0) {
			outputEntity.setValue(ExtortionOutputEntity.Field.MAFIOSO_CUSTODY.name(),
					true);
		}
		
		Event event = new Event(this.simulator.now() + this.custodyPDF.nextValue(),
				this, Constants.EVENT_RELEASE_CUSTODY);
		this.simulator.insert(event);
		
		this.custodyQueue.offer(action);
	}
	
	
	@Override
	public void decideConviction(CaptureMafiosoAction action) {
		
		int mafiosoId = (int) action
				.getParam(CaptureMafiosoAction.Param.MAFIOSO_ID);
		
		int extortionId = (int) action
				.getParam(CaptureMafiosoAction.Param.EXTORTION_ID);
		
		AbstractEntity outputEntity = null;
		if(extortionId >= 0) {
			outputEntity = OutputController.getInstance().getEntity(
					EntityType.EXTORTION, extortionId);
		}
		
		// Calculate conviction probability
		double convictionProb = this.conf.getConvictionProbability();
		if(this.collaborations.containsKey(mafiosoId)) {
			int numCollaborations = this.collaborations.get(mafiosoId);
			
			Evaluator eval = new Evaluator();
			eval.putVariable(COLLABORATION,
					(new Integer(numCollaborations)).toString());
			
			try {
				convictionProb += eval.getNumberResult(this.conf
						.getCollaborationConvictionFunction());
			} catch(EvaluationException e) {
				logger.debug(e.getMessage());
			}
		}
		
		// Decide whether the Mafioso is going to be imprisoned
		if(RandomUtil.nextDouble() < Math.max(0, Math.min(1, convictionProb))) {
			
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
			
			this.prisonQueue.add(imprisonment);
			
			Event releasePrison = new Event(this.simulator.now() + duration, this,
					Constants.EVENT_RELEASE_PRISON);
			this.simulator.insert(releasePrison);
			
			// Spread action
			this.spreadActionInformation(msg);
			
			// Output
			if(extortionId >= 0) {
				outputEntity.setValue(
						ExtortionOutputEntity.Field.MAFIOSO_CONVICTED.name(), true);
				outputEntity.setActive();
			}
			
			// Release the Mafioso of custody
		} else {
			
			ReleaseCustodyAction releaseCustody = new ReleaseCustodyAction(mafiosoId);
			
			Message msg = new Message(this.simulator.now(), this.id, mafiosoId,
					releaseCustody);
			this.sendMsg(msg);
			
			// Spread action
			this.spreadActionInformation(msg);
			
			// Output
			if(extortionId >= 0) {
				outputEntity.setValue(
						ExtortionOutputEntity.Field.MAFIOSO_CONVICTED.name(), false);
				outputEntity.setActive();
			}
		}
	}
	
	
	@Override
	public void receivePentito(PentitoAction action) {
		
		@SuppressWarnings("unchecked")
		List<Integer> mafiosoList = (List<Integer>) action
				.getParam(PentitoAction.Param.MAFIOSI_LIST);
		
		if(mafiosoList != null) {
			for(Integer mafiosoId : mafiosoList) {
				if(!this.mafiosiBlackList.contains(mafiosoId)) {
					this.mafiosiBlackList.add(mafiosoId);
				}
			}
		}
		
		int mafiosoId = (int) action.getParam(PentitoAction.Param.MAFIOSO_ID);
		
		@SuppressWarnings("unchecked")
		List<Integer> entrepreneurList = (List<Integer>) action
				.getParam(PentitoAction.Param.ENTREPRENEUR_LIST);
		
		if(entrepreneurList != null) {
			for(Integer entrepreneurId : entrepreneurList) {
				CollaborationRequestAction collaboration = new CollaborationRequestAction(
						mafiosoId, entrepreneurId);
				
				Message msg = new Message(this.simulator.now(), this.id,
						entrepreneurId, collaboration);
				this.sendMsg(msg);
			}
		}
		
		// Spread action
		Message msg = new Message(this.simulator.now(), mafiosoId, this.id, action);
		this.spreadActionInformation(msg);
	}
	
	
	@Override
	public void receiveCollaboration(CollaborateAction action) {
		int mafiosoId = (int) action.getParam(CollaborateAction.Param.MAFIOSO_ID);
		
		int numCollaborations = 0;
		if(this.collaborations.containsKey(mafiosoId)) {
			numCollaborations = this.collaborations.get(mafiosoId);
		}
		numCollaborations++;
		this.collaborations.put(mafiosoId, numCollaborations);
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
			
			StatePunishmentAction punishment = new StatePunishmentAction(this.id,
					entrepreneurId, this.conf.getNoCollaborationPunishment());
			
			Message msg = new Message(this.simulator.now(), this.id, entrepreneurId,
					punishment);
			this.sendMsg(msg);
			
			// Spread action
			this.spreadActionInformation(msg);
		}
	}
	
	
	@Override
	public void decideStateCompensation() {
		
		if(!this.assistQueue.isEmpty()) {
			DenouncePunishmentAction action = this.assistQueue.poll();
			
			int extortionId = (int) action
					.getParam(DenouncePunishmentAction.Param.EXTORTION_ID);
			
			double punishment = (double) action
					.getParam(DenouncePunishmentAction.Param.PUNISHMENT);
			
			// State has enough money to compensate the Entrepreneur
			if(this.fondoSolidarieta >= punishment) {
				
				this.fondoSolidarieta -= punishment;
				
				int entrepreneurId = (int) action
						.getParam(DenouncePunishmentAction.Param.ENTREPRENEUR_ID);
				
				StateCompensationAction compensation = new StateCompensationAction(
						extortionId, this.id, entrepreneurId, punishment);
				
				Message msg = new Message(this.simulator.now(), this.id,
						entrepreneurId, compensation);
				this.sendMsg(msg);
				
				// Spread action
				this.spreadActionInformation(msg);
				
				// Output
				AbstractEntity outputEntity = OutputController.getInstance().getEntity(
						EntityType.COMPENSATION, extortionId);
				outputEntity.setValue(
						CompensationOutputEntity.Field.STATE_COMPENSATED.name(), true);
				outputEntity.setValue(
						CompensationOutputEntity.Field.STATE_TIME_COMPENSATION.name(),
						this.simulator.now());
				outputEntity.setValue(
						CompensationOutputEntity.Field.STATE_COMPENSATION.name(),
						punishment);
				
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
	public void spreadNormativeInformation() {
		
		// Spread information to Consumers
		NormativeInfoAction notBuyPayExtortion = new NormativeInfoAction(this.id,
				Norms.BUY_FROM_NOT_PAYING_ENTREPRENEURS.name());
		
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
		
		NormativeInfoAction notPayExtortion = new NormativeInfoAction(this.id,
				Norms.NOT_PAY_EXTORTION.name());
		
		NormativeInfoAction denounceExtortion = new NormativeInfoAction(this.id,
				Norms.DENOUNCE.name());
		
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
		
		// Output
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				EntityType.NORMATIVE);
		outputEntity.setValue(NormativeOutputEntity.Field.TIME.name(),
				this.simulator.now());
		outputEntity.setValue(NormativeOutputEntity.Field.AGENT_ID.name(), this.id);
		outputEntity.setValue(NormativeOutputEntity.Field.NUMBER_CONSUMERS.name(),
				numConsumers);
		outputEntity.setValue(
				NormativeOutputEntity.Field.NUMBER_ENTREPRENEURS.name(),
				numEntrepreneurs);
		outputEntity.setActive();
		
		Event event = new Event(this.simulator.now()
				+ this.spreadInformationPDF.nextValue(), this,
				Constants.EVENT_SPREAD_INFORMATION);
		this.simulator.insert(event);
	}
	
	
	/**
	 * Selects an Entrepreneur for investigation
	 * 
	 * @param none
	 * @return Entrepreneur for investigation
	 */
	private int decideEntrepreneur() {
		
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
	private void increaseFondo() {
		
		this.fondoSolidarieta += this.conf.getResourceFondo();
		
		AbstractEntity outputEntity = OutputController.getInstance().getEntity(
				AbstractEntity.EntityType.STATE);
		outputEntity.setValue(StateOutputEntity.Field.TIME.name(),
				this.simulator.now());
		outputEntity.setValue(StateOutputEntity.Field.FONDO.name(),
				this.fondoSolidarieta);
		outputEntity.setActive();
		
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
	private void judgeMafioso() {
		
		if(!this.custodyQueue.isEmpty()) {
			CaptureMafiosoAction action = this.custodyQueue.poll();
			this.decideConviction(action);
		}
	}
	
	
	/**
	 * Release a imprisoned Mafioso
	 * 
	 * @param none
	 * @return none
	 */
	private void releaseMafioso() {
		
		if(!this.prisonQueue.isEmpty()) {
			ImprisonmentAction prison = this.prisonQueue.poll();
			
			int mafiosoId = (int) prison
					.getParam(ImprisonmentAction.Param.MAFIOSO_ID);
			
			ReleaseImprisonmentAction action = new ReleaseImprisonmentAction(
					mafiosoId);
			
			Message msg = new Message(this.simulator.now(), this.id, mafiosoId,
					action);
			this.sendMsg(msg);
			
			// Spread action
			this.spreadActionInformation(msg);
		}
	}
	
	
	/**
	 * Spread action information
	 * 
	 * @param msg
	 *          Spread action message to a proportion of consumers and
	 *          entrepreneurs, and also to the Intermediary Organization
	 * @return none
	 */
	private void spreadActionInformation(Message msg) {
		
		// Spread to a proportion of Consumers
		int numConsumers = (int) (this.consumers.size() * this.conf
				.getProportionCustomers());
		List<Integer> consumerIds = new ArrayList<Integer>();
		while(consumerIds.size() < numConsumers) {
			
			int consumerId = (int) this.consumers.keySet().toArray()[RandomUtil
					.nextIntFromTo(0, (this.consumers.size() - 1))];
			
			if(!consumerIds.contains(consumerId)) {
				consumerIds.add(consumerId);
				
				Message newMsg = new Message(this.simulator.now(), this.id, consumerId,
						msg);
				this.sendMsg(newMsg);
			}
		}
		
		// Spread to a proportion of Entrepreneurs
		int numEntrepreneurs = (int) (this.entrepreneurs.size() * this.conf
				.getProportionEntrepreneurs());
		List<Integer> entrepreneurIds = new ArrayList<Integer>();
		int qtyEntrepreneurs = this.entrepreneurs.size();
		while(entrepreneurIds.size() < numEntrepreneurs) {
			
			int entrepreneurId = (int) this.entrepreneurs.keySet().toArray()[RandomUtil
					.nextIntFromTo(0, (qtyEntrepreneurs - 1))];
			
			if(!entrepreneurIds.contains(entrepreneurId)) {
				entrepreneurIds.add(entrepreneurId);
				
				Message newMsg = new Message(this.simulator.now(), this.id,
						entrepreneurId, msg);
				this.sendMsg(newMsg);
			}
		}
		
		// Send to the Intermediary Organization
		Message newMsg = new Message(this.simulator.now(), this.id, this.ioId, msg);
		this.sendMsg(newMsg);
	}
	
	
	@Override
	public void finalizeSim() {
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
			
			// Denounce extortion
			if(content instanceof DenounceExtortionAction) {
				this.decideInvestigateExtortion((DenounceExtortionAction) content);
				
				// Denounce punishment
			} else if(content instanceof DenouncePunishmentAction) {
				this.decideInvestigatePunishment((DenouncePunishmentAction) content);
				
				// Pentito
			} else if(content instanceof PentitoAction) {
				this.receivePentito((PentitoAction) content);
				
				// Release investigation
			} else if(content instanceof ReleaseInvestigationAction) {
				this.releaseInvestigation((ReleaseInvestigationAction) content);
				
				// Capture Mafioso
			} else if(content instanceof CaptureMafiosoAction) {
				this.decideCustody((CaptureMafiosoAction) content);
				
				// Collaboration
			} else if(content instanceof CollaborateAction) {
				this.receiveCollaboration((CollaborateAction) content);
				
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
			case Constants.EVENT_RELEASE_PRISON:
				this.releaseMafioso();
				break;
			case Constants.EVENT_ASSIST_ENTREPRENEUR:
				this.decideStateCompensation();
				break;
			case Constants.EVENT_SPREAD_INFORMATION:
				this.spreadNormativeInformation();
		}
	}
}