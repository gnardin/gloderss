package gloderss.output;

public class ExtortionOutputEntity extends AbstractEntity {
	
	public enum Field {
		EXTORTION_ID("id", DataType.INTEGER), CYCLE("cycle", DataType.INTEGER), ENTREPRENEUR_ID(
				"entrepreneurId", DataType.INTEGER), MAFIOSO_ID("mafiosoId",
				DataType.INTEGER), MAFIA_EXTORTION("mafiaExtortion", DataType.DOUBLE), MAFIA_PUNISHMENT(
				"mafiaPunishment", DataType.DOUBLE), MAFIA_BENEFIT("mafiaBenefit",
				DataType.DOUBLE), PAID("paid", DataType.BOOLEAN), DENOUNCED_EXTORTION(
				"denouncedExtortion", DataType.BOOLEAN), MAFIA_PUNISHED(
				"mafiaPunished", DataType.BOOLEAN), MAFIA_BENEFITED("mafiaBenefited",
				DataType.BOOLEAN), DENOUNCED_PUNISHMENT("denouncedPunishment",
				DataType.BOOLEAN), COLLABORATION_REQUESTED("collaborationRequested",
				DataType.BOOLEAN), COLLABORATED("collaborated", DataType.BOOLEAN), STATE_PUNISHED(
				"statePunished", DataType.BOOLEAN), STATE_PUNISHMENT("statePunishment",
				DataType.BOOLEAN), COMPENSATED("compensated", DataType.BOOLEAN), STATE_COMPENSATION(
				"stateCompensation", DataType.DOUBLE), IMPRISONED("imprisoned",
				DataType.BOOLEAN);
		
		private String		name;
		
		private DataType	type;
		
		
		Field(String name, DataType type) {
			this.name = name;
			this.type = type;
		}
		
		
		public String getName() {
			return this.name;
		}
		
		
		public DataType getType() {
			return this.type;
		}
	}
	
	private String		separator;
	
	private Object[]	entity;
	
	
	public ExtortionOutputEntity(int id, String separator) {
		super(id);
		this.separator = separator;
		this.entity = new Object[Field.values().length];
		this.entity[Field.EXTORTION_ID.ordinal()] = id;
	}
	
	
	@Override
	public void setValue(String fieldStr, Object value) {
		
		Field field = Field.valueOf(fieldStr);
		
		if(field.getType().equals(DataType.BOOLEAN)) {
			this.entity[field.ordinal()] = (Boolean) value;
			
		} else if(field.getType().equals(DataType.DOUBLE)) {
			this.entity[field.ordinal()] = (Double) value;
			
		} else if(field.getType().equals(DataType.INTEGER)) {
			this.entity[field.ordinal()] = (Integer) value;
		}
	}
	
	
	@Override
	public Object getValue(String fieldStr) {
		return this.entity[Field.valueOf(fieldStr).ordinal()];
	}
	
	
	@Override
	public String getLine() {
		String line = new String();
		
		Object value;
		for(Field field : Field.values()) {
			value = this.entity[field.ordinal()];
			if(value == null) {
				value = (String) "";
			}
			line += value + this.separator;
		}
		line = line.substring(0, line.length() - 1);
		
		return line;
	}
	
	
	@Override
	public String getHeader() {
		String header = new String();
		
		for(Field field : Field.values()) {
			header += field.getName() + this.separator;
		}
		header = header.substring(0, header.length() - 1);
		
		return header;
	}
}