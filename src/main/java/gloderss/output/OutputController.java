package gloderss.output;

import gloderss.Constants;
import gloderss.conf.OutputConf;
import gloderss.engine.devs.EventSimulator;
import gloderss.engine.event.Event;
import gloderss.engine.event.EventHandler;
import gloderss.output.AbstractEntity.EntityType;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputController implements EventHandler {
	
	private final static Logger														logger	= LoggerFactory
																																		.getLogger(OutputController.class);
	
	private static OutputController												instance;
	
	private EventSimulator																simulator;
	
	private int																						replication;
	
	private String																				directory;
	
	private OpenOption[]																	options;
	
	private String																				separator;
	
	private int																						timeToWrite;
	
	private Map<EntityType, BufferedWriter>								file;
	
	private Map<EntityType, Integer>											entityId;
	
	private Map<EntityType, Map<Integer, AbstractEntity>>	entities;
	
	private Map<EntityType, Boolean>											firstWrite;
	
	
	public OutputController(EventSimulator simulator, OutputConf conf) {
		this.simulator = simulator;
		
		this.directory = conf.getDirectory();
		
		this.options = new OpenOption[4];
		if(conf.getAppend()) {
			this.options[0] = StandardOpenOption.CREATE;
			this.options[1] = StandardOpenOption.WRITE;
			this.options[2] = StandardOpenOption.SYNC;
			this.options[3] = StandardOpenOption.APPEND;
		} else {
			this.options[0] = StandardOpenOption.CREATE;
			this.options[1] = StandardOpenOption.WRITE;
			this.options[2] = StandardOpenOption.SYNC;
			this.options[3] = StandardOpenOption.TRUNCATE_EXISTING;
		}
		
		this.separator = conf.getSeparator();
		this.timeToWrite = conf.getTimeToWrite();
		
		this.replication = -1;
		
		instance = this;
	}
	
	
	public void initializeSim() {
		Event event = new Event(this.simulator.now() + this.timeToWrite, this,
				Constants.EVENT_WRITE_DATA);
		this.simulator.insert(event);
	}
	
	
	public static OutputController getInstance() {
		return instance;
	}
	
	
	public void newInstance(int replica) {
		if(this.replication != replica) {
			this.file = new HashMap<EntityType, BufferedWriter>();
			this.entities = new LinkedHashMap<EntityType, Map<Integer, AbstractEntity>>();
			this.entityId = new HashMap<EntityType, Integer>();
			this.firstWrite = new HashMap<EntityType, Boolean>();
			
			this.replication = replica;
		}
	}
	
	
	public void init(EntityType type, String filename) {
		
		if(!this.file.containsKey(type)) {
			
			try {
				Path dirPath = FileSystems.getDefault().getPath(
						this.directory + FileSystems.getDefault().getSeparator()
								+ this.replication);
				Files.createDirectories(dirPath);
				
				Path pFile = FileSystems.getDefault().getPath(dirPath.toString(),
						filename);
				
				BufferedWriter file = Files.newBufferedWriter(pFile,
						Charset.forName(Constants.ENCONDING), this.options);
				
				this.file.put(type, file);
				
				Map<Integer, AbstractEntity> typeEntities = new HashMap<Integer, AbstractEntity>();
				this.entities.put(type, typeEntities);
				
				this.entityId.put(type, 0);
				this.firstWrite.put(type, true);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public Collection<AbstractEntity> getEntities(EntityType type) {
		if(this.entities.containsKey(type)) {
			return this.entities.get(type).values();
		}
		
		return new LinkedList<AbstractEntity>();
	}
	
	
	public synchronized AbstractEntity getEntity(EntityType type) {
		AbstractEntity entity = null;
		
		if(this.entityId.containsKey(type)) {
			
			int id = this.entityId.get(type);
			
			entity = this.getEntity(type, id);
			
			if(entity != null) {
				this.entityId.put(type, id + 1);
			}
		}
		
		return entity;
	}
	
	
	public synchronized AbstractEntity getEntity(EntityType type, int id) {
		AbstractEntity entity = null;
		
		Map<Integer, AbstractEntity> typeEntities;
		if(this.entities.containsKey(type)) {
			typeEntities = this.entities.get(type);
		} else {
			typeEntities = new HashMap<Integer, AbstractEntity>();
		}
		
		if(!typeEntities.containsKey(id)) {
			switch(type) {
				case EXTORTION:
					entity = new ExtortionOutputEntity(id, this.separator);
					break;
				case COMPENSATION:
					entity = new CompensationOutputEntity(id, this.separator);
					break;
				case PURCHASE:
					entity = new PurchaseOutputEntity(id, this.separator);
					break;
				case NORMATIVE:
					entity = new NormativeOutputEntity(id, this.separator);
					break;
				case ENTREPRENEUR:
					entity = new EntrepreneurOutputEntity(id, this.separator);
					break;
				case CONSUMER:
					entity = new ConsumerOutputEntity(id, this.separator);
					break;
				case MAFIA:
					entity = new MafiaOutputEntity(id, this.separator);
					break;
				case MAFIOSI:
					entity = new MafiosiOutputEntity(id, this.separator);
					break;
				case STATE:
					entity = new StateOutputEntity(id, this.separator);
					break;
				case INTERMEDIARY_ORGANIZATION:
					entity = new IntermediaryOrganizationOutputEntity(id, this.separator);
					break;
			}
			
			if(entity != null) {
				typeEntities.put(id, entity);
				this.entities.put(type, typeEntities);
			}
		} else {
			entity = typeEntities.get(id);
		}
		
		return entity;
	}
	
	
	public void setEntity(EntityType type, AbstractEntity entity) {
		int id = entity.getEntityId();
		
		Map<Integer, AbstractEntity> typeEntities;
		if(this.entities.containsKey(type)) {
			typeEntities = this.entities.get(type);
		} else {
			typeEntities = new HashMap<Integer, AbstractEntity>();
		}
		
		typeEntities.put(id, entity);
		this.entities.put(type, typeEntities);
	}
	
	
	public void write(boolean active) throws IOException {
		
		for(EntityType type : EntityType.values()) {
			
			Map<Integer, AbstractEntity> typeEntities = new HashMap<Integer, AbstractEntity>();
			typeEntities.putAll(this.entities.get(type));
			for(Integer id : this.entities.get(type).keySet()) {
				if(typeEntities.containsKey(id)) {
					AbstractEntity entity = typeEntities.get(id);
					if((!active) && (!entity.isActive())) {
						typeEntities.remove(id);
					}
				}
			}
			
			for(Integer id : typeEntities.keySet()) {
				this.entities.remove(id);
			}
			
			if((typeEntities != null) && (!typeEntities.isEmpty())) {
				
				BufferedWriter bFile = this.file.get(type);
				
				for(Integer id : typeEntities.keySet()) {
					if(this.firstWrite.get(type)) {
						bFile.write(typeEntities.get(id).getHeader());
						bFile.newLine();
						this.firstWrite.put(type, false);
					}
					
					if(typeEntities.get(id).isActive()) {
						AbstractEntity entity = typeEntities.get(id);
						bFile.write(entity.getLine());
						bFile.newLine();
					}
				}
				
				typeEntities.clear();
				
				this.entities.put(type, typeEntities);
				bFile.flush();
			}
		}
	}
	
	
	@Override
	public void handleEvent(Event event) {
		
		switch((String) event.getCommand()) {
			case Constants.EVENT_WRITE_DATA:
				try {
					this.write(false);
					
					// Schedule the next writing event
					Event nextEvent = new Event(this.simulator.now() + this.timeToWrite,
							this, Constants.EVENT_WRITE_DATA);
					this.simulator.insert(nextEvent);
					
				} catch(IOException e) {
					logger.debug(e.toString());
				}
				break;
		}
	}
}