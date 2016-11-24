package de.appsist.service.msd;


import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.commons.event.SetMachineDataEvent;
import de.appsist.commons.event.SetMachineDataEvent.Field;
import de.appsist.commons.util.EventUtil;
import de.appsist.service.middrv.entity.Machine;
import de.appsist.service.middrv.entity.MachineData;
import de.appsist.service.middrv.entity.MachineSchema;
import de.appsist.service.middrv.entity.MachineValueSpecification;
import de.appsist.service.middrv.entity.MachineValueType;
import de.appsist.service.middrv.entity.Unit;
import de.appsist.service.middrv.entity.VisualizationLevel;
import de.appsist.service.middrv.entity.VisualizationType;
import de.appsist.service.middrv.rest.ContentType;
import de.appsist.service.middrv.rest.DataMessage;
import de.appsist.service.middrv.rest.DataSchemaMismatchException;
import de.appsist.service.middrv.rest.SchemaMessage;
import de.appsist.service.middrv.rest.client.RestClient;

public class SetMachineDataEventHandler implements Handler<Message<JsonObject>> {
	private static final Logger logger = LoggerFactory.getLogger(SetMachineDataEventHandler.class);
	
	private final HttpClient midClient;
	private final String midBasePath;
	private RestClient client;
	
	public static String ADDRESS = "appsist:event:" + SetMachineDataEvent.MODEL_ID;
	
	public SetMachineDataEventHandler(Vertx vertx, int port, boolean secure, String midBasePath) {
		midClient = vertx.createHttpClient();
		midClient.setHost("localhost");
		midClient.setPort(port);
		midClient.setSSL(secure);
		this.midBasePath = midBasePath;
	}
	
	public void initializeRESTConnection() {
		client = new RestClient(midClient.getHost(), midClient.getPort(), this.midBasePath);
	}
	
	@Override
	public void handle(Message<JsonObject> message) {
		SetMachineDataEvent event = EventUtil.parseEvent(message.body().toMap(), SetMachineDataEvent.class);
		logger.debug("Received Event: " + event);

		Machine machine = new Machine(event.getVendorId(), event.getMachineId(), event.getSerialNumber());
		
		MachineSchema machineSchema = new MachineSchema(machine, event.getStationId(), event.getSiteId());
		MachineData machineData = new MachineData(machine);
		
		for (Field f : event.getFields()) {
			MachineValueType machineValueType = MachineValueType.byIdentifier(f.getMachineValueType());
			
			Unit unit = new Unit(f.getUnit());
			VisualizationType visualizationType = VisualizationType.byName(f.getVisualizationType());
			VisualizationLevel visualizationLevel = VisualizationLevel.byName(f.getVisualizationLevel());
			machineSchema.addField(new MachineValueSpecification(f.getName(),  machineValueType, unit, visualizationType, visualizationLevel));
			
			switch(machineValueType) {
			case BOOL:
				boolean v1 = Boolean.getBoolean(f.getValue());
				machineData.put(f.getName(), v1);
				break;
			case LONG:
				long v2 = Long.parseLong(f.getValue());
				machineData.put(f.getName(), v2);
				break;
			case DOUBLE:
				double v3 = Double.parseDouble(f.getValue());
				machineData.put(f.getName(), v3);
				break;
			case STRING:
				machineData.put(f.getName(), f.getValue());
				break;
			}
		}
		
		SchemaMessage schemaMessage = new SchemaMessage();
		schemaMessage.addSchema(machineSchema);
		
		DataMessage dataMessage = new DataMessage();
 		try {
 			dataMessage.addMachineData(machineData, machineSchema);
 		} catch (DataSchemaMismatchException e) {
 			logger.error("ERROR: There was a mismatch between the schema and data of SetMachineDataEvent! NO DATA sent!");
 		}
 		// Send
 		client.send(ContentType.JSON, schemaMessage);
 		client.send(ContentType.JSON, dataMessage);
 		// End
 		logger.debug("SetMachineDataEvent has been forwarded to REST API.");
	}
}
