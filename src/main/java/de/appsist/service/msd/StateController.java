package de.appsist.service.msd;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.commons.event.SetMachineDataEvent;
import de.appsist.commons.event.SetMachineDataEvent.Field;
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

/**
 * Controller for machine states.
 * @author nico.herbig@dfki.de
 */
public class StateController {
	private static final Logger logger = LoggerFactory.getLogger(StateController.class);
	private Vertx vertx;
	
	private final HttpClient midClient;
	private final String midBasePath;
	private final JsonObject config;
	private boolean autoSend=true;
	
	private Random rand;
	
	private RestClient client;

	// Festo Pilot Scenario
	private Machine fpilotMachine;
	private MachineSchema fpilotMachineSchema;
	private SchemaMessage fpilotSchemaMessage;
	//private String fpilotLoctiteFieldName = "Loctite";
	//private double fpilotLoctite = 1.0;
	private String fpilotFatFieldName = "Fett";
	private double fpilotFat = 1.0;
	private String fpilotDNCFieldName = "DNC";
	private String fpilotDNC = "ST20";
	private String fpilotAutomationModeFieldName = "Automatikmodus aktiv";
	private boolean fpilotAutomationModeActive = true;
	private String fpilotMachineLockedFiledName = "Verriegelung aktiv";
	private boolean fpilotMachineLocked = true;
	private String fpilotDoor1ClosedFieldName = "Tuer 1 geschlossen";
	private boolean fpilotDoor1Closed = true;
	private String fpilotDoor2ClosedFieldName = "Tuer 2 geschlossen";
	private boolean fpilotDoor2Closed = true;
	private String fpilotDoor3ClosedFieldName = "Tuer 3 geschlossen";
	private boolean fpilotDoor3Closed = true;
	private String fpilotDoor4ClosedFieldName = "Tuer 4 geschlossen";
	private boolean fpilotDoor4Closed = true;
	private String fpilotLoctiteFullFieldName = "Fuellstand Loctite im Sollbereich";
	private boolean fpilotLoctiteFull = true;
	
	// Festo Cebit scenario
	private Machine fcebitMachine;
	private MachineSchema fcebitMachineSchema;
	private SchemaMessage fcebitSchemaMessage;
	private String fcebitStateOkFieldName = "state_ok";
	private boolean fcebitStateOk = true;
	private String fcebitQ1FieldName = "q1";
	private boolean fcebitQ1 = false;
	private String fcebitQ2FieldName = "q2";
	private boolean fcebitQ2 = false;
	private String fcebitLostPartFieldName = "Teil verloren";
	private boolean fcebitLostPart = false;
	private String fcebitDoorOpenFieldName = "Tuer offen";
	private String fcebitCapStorageEmptyFieldName = "Deckelmagazin leer";
	private String fcebitSpringStorageEmptyFieldName = "Federmagazin leer";
	
	// MBB scenario
	private Machine mbbMachine;
	private MachineSchema mbbMachineSchema;
	private SchemaMessage mbbSchemaMessage;
	private String mbbLostPartFieldName = "Bauteil fehlt";
	private boolean mbbLostPart = false;
	private String mbbPartCounterFieldName = "Teilezaehler";
	private Long mbbPartCounter = 2L;
	private String mbbManualModeFieldName = "Handbetrieb";
	private boolean mbbManualMode = false;
	private String mbbTagAvailableFieldName = "Tag verfuegbar";
	private boolean mbbTagAvailable = false;
	private String mbbPartAvailableFieldName = "Bauteil verfuegbar";
	private String mbbDoorOpenFieldName = "Tuer offen";
	private boolean mbbDoorOpen = false;
	
	public StateController(Vertx vertx, int port, boolean secure, String midBasePath, JsonObject config) {
		this.vertx = vertx;
		midClient = vertx.createHttpClient();
		midClient.setHost("localhost");
		midClient.setPort(port);
		midClient.setSSL(secure);
		this.midBasePath = midBasePath;
		this.config = config;
		
		this.rand = new Random();
	}
	
	public void initializeRESTConnection() {
		// RestClient
		client = new RestClient(midClient.getHost(), midClient.getPort(), this.midBasePath);
		
		autoSend=config.getBoolean("autosend",true);

		logger.info("MSD autosend " + (autoSend ? " on" : " off"));
		
		if (!autoSend) return;
		
		// Festo Pilot Scenario
		fpilotMachine = new Machine("Festo", "Station20", "1111111", "http://www.appsist.de/ontology/festo/DNC_DNCB_DSBC");

		fpilotMachineSchema = new MachineSchema(fpilotMachine, "Station20", "DNC_DNCB_DSBC_Automation", "http://www.appsist.de/ontology/festo/S20");
		// Dummy values
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotFatFieldName, MachineValueType.DOUBLE, Unit.NONE, VisualizationType.PRECENT_BAR, VisualizationLevel.OVERVIEW));
		// Real values
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotDNCFieldName, MachineValueType.STRING, Unit.NONE, VisualizationType.TEXT_FIELD, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotAutomationModeFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotMachineLockedFiledName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotDoor1ClosedFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotDoor2ClosedFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotDoor3ClosedFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotDoor4ClosedFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
		fpilotMachineSchema.addField(new MachineValueSpecification(fpilotLoctiteFullFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));

		
		fpilotSchemaMessage = new SchemaMessage();
		fpilotSchemaMessage.addSchema(fpilotMachineSchema);
		
		sendFestoPilotData();
		
		
		// Festo Cebit Scenario
        fcebitMachine = new Machine("Anlage1", "Maschine20", "RV-2FB Robot Arm Controller", "http://www.appsist.de/ontology/demonstrator/Demonstrator");
        
        fcebitMachineSchema = new MachineSchema(fcebitMachine, "Anlage1", "Anlage1", "http://www.appsist.de/ontology/demonstrator/StationMontage");
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitStateOkFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.NEVER));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitQ1FieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.NEVER));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitQ2FieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.NEVER));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitLostPartFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitDoorOpenFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitCapStorageEmptyFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        fcebitMachineSchema.addField(new MachineValueSpecification(fcebitSpringStorageEmptyFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        
        fcebitSchemaMessage = new SchemaMessage();
        fcebitSchemaMessage.addSchema(fcebitMachineSchema);
        
        
        
        
        sendFestoCebitData();
        
        // MBB Scenario
        mbbMachine = new Machine("MBB", "MVM700", "MVM700T-009");
        
        mbbMachineSchema = new MachineSchema(mbbMachine, "1", "TAL01");
        mbbMachineSchema.addField(new MachineValueSpecification(mbbLostPartFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        mbbMachineSchema.addField(new MachineValueSpecification(mbbPartCounterFieldName, MachineValueType.LONG, Unit.NONE, VisualizationType.TEXT_FIELD, VisualizationLevel.OVERVIEW));
        mbbMachineSchema.addField(new MachineValueSpecification(mbbManualModeFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        mbbMachineSchema.addField(new MachineValueSpecification(mbbTagAvailableFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        mbbMachineSchema.addField(new MachineValueSpecification(mbbPartAvailableFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
        mbbMachineSchema.addField(new MachineValueSpecification(mbbDoorOpenFieldName, MachineValueType.BOOL, Unit.NONE, VisualizationType.ON_OFF_LIGHT, VisualizationLevel.OVERVIEW));
               
        mbbSchemaMessage = new SchemaMessage();
        mbbSchemaMessage.addSchema(mbbMachineSchema);
        
        sendMBBData();
	}
	
	public void performAction(JsonObject action, HttpServerResponse response) {
		String method = action.getString("method");
		response.headers().add("Content-Type", "text/plain");
		switch (method) {
		case "emptyLoctite":
			festoPilotSetLoctite(response, false);
			break;
		case "fillLoctite":
			festoPilotSetLoctite(response, true);
			break;
		case "emptyFat":
			festoPilotSetFat(response, false);
			break;
		case "fillFat":
			festoPilotSetFat(response, true);
			break;
		case "fpilotDoorOpenToTrue":
			festoPilotSetDoorOpen(response, true);
			break;
		case "fpilotDoorOpenToFalse":
			festoPilotSetDoorOpen(response, false);
			break;
		case "fpilotLockedToFalse":
			festoPilotSetLocked(response, false);
			break;
		case "fpilotLockedToTrue":
			festoPilotSetLocked(response, true);
			break;
		case "fpilotAutomaticModeToFalse":
			festoPilotSetAutomaticMode(response, false);
			break;
		case "fpilotAutomaticModeToTrue":
			festoPilotSetAutomaticMode(response, true);
			break;
		case "festoStateOkToTrue":
			setFestoCebitStateOk(response, true);
			break;
		case "festoStateOkToFalse":
			setFestoCebitStateOk(response, false);
			break;
		case "festoQ1ToTrue":
			setFestoCebitQ1(response, true);
			break;
		case "festoQ1ToFalse":
			setFestoCebitQ1(response, false);
			break;
		case "festoQ2ToTrue":
			setFestoCebitQ2(response, true);
			break;
		case "festoQ2ToFalse":
			setFestoCebitQ2(response, false);
			break;
		case "festoLostPartToTrue":
			setFestoLostPart(response, true);
			break;
		case "festoLostPartToFalse":
			setFestoLostPart(response, false);
			break;
		case "start10HzSimulation":
			start10HzSimulation(response);
			break;
		case "stop10HzSimulation":
			stop10HzSimulation(response);
			break;
		case "start100HzSimulation":
			start100HzSimulation(response);
			break;
		case "stop100HzSimulation":
			stop100HzSimulation(response);
			break;
		case "mbbLostPartToTrue":
			setMBBLostPart(response, true);
			break;
		case "mbbLostPartToFalse":
			setMBBLostPart(response, false);
			break;
		case "mbbDoorOpenToTrue":
			setMBBDoorOpen(response, true);
			break;
		case "mbbDoorOpenToFalse":
			setMBBDoorOpen(response, false);
			break;
		case "mbbManualModeToTrue":
			setMBBManualMode(response, true);
			break;
		case "mbbManualModeToFalse":
			setMBBManualMode(response, false);
			break;
		default:
			logger.warn("Invalid method: " + method);
		}
	}
	
	public void performActionWithParam(JsonObject action, HttpServerResponse response) {
		String method = action.getString("method");
		String param = action.getString("param");
		response.headers().add("Content-Type", "text/plain");
		switch (method) {
		case "reportWeldSeamError":
			festoPilotReportWeldSeamError(response, param);
			break;
		default:
			logger.warn("Invalid method: " + method);
		}
	}
	
	// Festo Cebit Scenario
	private void sendFestoCebitData() {
		if (fcebitMachine == null) {
			throw new IllegalStateException("RESTConnection was not initialized");
		}
		MachineData fmachineData=new MachineData(fcebitMachine);
		fmachineData.put(fcebitStateOkFieldName, fcebitStateOk);
		fmachineData.put(fcebitQ1FieldName, fcebitQ1);
		fmachineData.put(fcebitQ2FieldName, fcebitQ2);
		fmachineData.put(fcebitLostPartFieldName, fcebitLostPart);
		fmachineData.put(fcebitDoorOpenFieldName, !fcebitStateOk);
		fmachineData.put(fcebitSpringStorageEmptyFieldName, fcebitQ1);
		fmachineData.put(fcebitCapStorageEmptyFieldName, fcebitQ2);
	 	 

        // Data Message
 		DataMessage fdataMessage = new DataMessage();
 		try {
 			fdataMessage.addMachineData(fmachineData, fcebitMachineSchema);
 		} catch (DataSchemaMismatchException e) {
 			logger.error("ERROR: There was a mismatch between the Festo Cebit schema and data! NO DATA sent!");
 		}
 		// Send
 		client.send(ContentType.JSON, fcebitSchemaMessage);
 		client.send(ContentType.JSON, fdataMessage);
 		// End
 		logger.info("Festo Cebit data has been updated.");
	}
	
	private void setFestoCebitStateOk(final HttpServerResponse response, boolean state) {
		logger.info("Setting stateOk to " + state + " for Festo Cebit Scenario.");
		fcebitStateOk = state;
		sendFestoCebitData();
		response.end();
	}
	
	private void setFestoCebitQ1(final HttpServerResponse response, boolean state) {
		logger.info("Setting q1 to " + state + " for Festo Cebit Scenario.");
		fcebitQ1 = state;
		sendFestoCebitData();
		response.end();
	}
	
	private void setFestoCebitQ2(final HttpServerResponse response, boolean state) {
		logger.info("Setting q2 to " + state + " for Festo Cebit Scenario.");
		fcebitQ2 = state;
		sendFestoCebitData();
		response.end();
	}
	
	private void setFestoLostPart(final HttpServerResponse response, boolean state) {
		logger.info("Setting lost part to " + state + " for Festo Cebit Scenario.");
		fcebitLostPart = state;
		sendFestoCebitData();
		response.end();
	}
	
	private void sendFestoCebitRandomData() {
		fcebitQ1 = rand.nextBoolean();
		fcebitQ2 = rand.nextBoolean();
		fcebitLostPart = rand.nextBoolean();
		fcebitStateOk = rand.nextBoolean();
		sendFestoCebitData();
	}
	
	private AtomicBoolean simulation10HzIsRunning = new AtomicBoolean(false);
	private AtomicBoolean simulation100HzIsRunning = new AtomicBoolean(false);
	
	private void start10HzSimulation(final HttpServerResponse response) {
		logger.info("10 Hz data sending started");
		simulation10HzIsRunning.set(true);
		
		new Thread() {
			public void run() {
				while (simulation10HzIsRunning.get()) {
					try {
						Thread.currentThread().sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendFestoCebitRandomData();
				}
			}
		}.start();
		
		response.end();
	}
	
	private void stop10HzSimulation(final HttpServerResponse response) {
		logger.info("10 Hz data sending stopped");
		simulation10HzIsRunning.set(false);
		response.end();
	}
	
	private void start100HzSimulation(final HttpServerResponse response) {
		logger.info("100 Hz data sending started");
		simulation100HzIsRunning.set(true);
		
		new Thread() {
			public void run() {
				while (simulation100HzIsRunning.get()) {
					try {
						Thread.currentThread().sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendFestoCebitRandomData();
				}
			}
		}.start();
		
		response.end();
	}
	
	private void stop100HzSimulation(final HttpServerResponse response) {
		logger.info("100 Hz data sending stopped");
		simulation100HzIsRunning.set(false);
		response.end();
	}
	
	// End Festo Cebit Scenario
	
	// Festo Pilot Scenario
	private void sendFestoPilotData() {
		if (fpilotMachine == null) {
			throw new IllegalStateException("RESTConnection was not initialized");
		}
		// Machine Data
		MachineData machineData = new MachineData(fpilotMachine);
		// Dummy values
		machineData.put(fpilotFatFieldName, fpilotFat);
		// Real values
		machineData.put(fpilotDNCFieldName, fpilotDNC);
		machineData.put(fpilotAutomationModeFieldName, fpilotAutomationModeActive);
		machineData.put(fpilotMachineLockedFiledName, fpilotMachineLocked);
		machineData.put(fpilotDoor1ClosedFieldName, fpilotDoor1Closed);
		machineData.put(fpilotDoor2ClosedFieldName, fpilotDoor2Closed);
		machineData.put(fpilotDoor3ClosedFieldName, fpilotDoor3Closed);
		machineData.put(fpilotDoor4ClosedFieldName, fpilotDoor4Closed);
		machineData.put(fpilotLoctiteFullFieldName, fpilotLoctiteFull);
		// Data Message
		DataMessage dataMessage = new DataMessage();
		try {
			dataMessage.addMachineData(machineData, fpilotMachineSchema);
		} catch (DataSchemaMismatchException e) {
			e.printStackTrace();
			logger.error("There was a mismatch between the Festo Pilot schema and data");
		}
		// Send
		client.send(ContentType.JSON, fpilotSchemaMessage);
		client.send(ContentType.JSON, dataMessage);
		// End
		logger.info("Festo Pilot data has been updated.");
	}
	
	private void festoPilotSetLoctite(final HttpServerResponse response, boolean fill) {
		festoPilotSetLoctite(fill);
		response.end();
	}
	
	public void festoPilotSetLoctite(boolean fill) {
		fpilotLoctiteFull = fill;
		/*if (!fill) {
			// Generate random almost empty value
			double randomValue = 0.09 * rand.nextDouble();
			fpilotLoctite = randomValue;
		} else {
			fpilotLoctite = 1.0;
		}*/
		// Send
		sendFestoPilotData();
		// End
		if (!fill)
			logger.info("Loctite has been emptied.");
		else
			logger.info("Loctite has been filled.");
	}
	
	private void festoPilotSetFat(final HttpServerResponse response, boolean fill) {
		festoPilotSetFat(fill);
		response.end();
	}
	
	public void festoPilotSetFat(boolean fill) {
		if (!fill) {
			// Generate random almost empty value
			double randomValue = 0.09 * rand.nextDouble();
			fpilotFat = randomValue;
		} else {
			fpilotFat = 1.0;
		}
		// Send
		sendFestoPilotData();
		// End
		if (!fill)
			logger.info("Fat has been emptied.");
		else 
			logger.info("Fat has been filled.");
	}
	
	private void festoPilotSetDoorOpen(final HttpServerResponse response, boolean state) {
		logger.info("Setting open door to " + state + " for festo pilot scenario.");
		fpilotDoor3Closed = state;
		fpilotDoor4Closed = state;
		sendFestoPilotData();
		response.end();
	}
	
	private void festoPilotSetLocked(final HttpServerResponse response, boolean state) {
		logger.info("Setting locked to " + state + " for festo pilot scenario");
		fpilotMachineLocked = state;
		sendFestoPilotData();
		response.end();
	}
	
	private void festoPilotSetAutomaticMode(final HttpServerResponse response, boolean state) {
		logger.info("Setting automatc mode to " + state + " for festo pilot scenario.");
		fpilotAutomationModeActive = state;
		sendFestoPilotData();
		response.end();
	}
	
	public void festoPilotReportWeldSeamError(final HttpServerResponse response, String inputFieldValue) {
		logger.debug("Creating appsist SetMachineDataEvent to report weld seam error: " + inputFieldValue);
		List<Field> fields = new ArrayList<SetMachineDataEvent.Field>();
        Field field = new Field(
                      "Schweissnahtfehler",
                      "string",
                      "",
                      "text_field",
                      "overview",
                      inputFieldValue);
        fields.add(field);
        
        JsonObject machineJson = config.getObject("machine");
        JsonObject stationJson = config.getObject("station");
        String uniqueID = UUID.randomUUID().toString();

        SetMachineDataEvent event = new SetMachineDataEvent(
        			  uniqueID,
                      machineJson.getString("vendorId"),
                      machineJson.getString("machineID"),
                      machineJson.getString("serialNumber"),
                      stationJson.getString("stationId"),
                      stationJson.getString("siteId"),
                      fields);
        
        vertx.eventBus().publish("appsist:event:" + SetMachineDataEvent.MODEL_ID, new JsonObject(event.asMap()));

		response.end();
		logger.debug("Published SetMachineDataEvent");
	}
	// End Festo Pilot Scenario

	
	// MBB Scenario
	private void sendMBBData() {
		if (mbbMachine == null) {
			throw new IllegalStateException("RESTConnection was not initialized");
		}
		// Machine Data
		MachineData machineData = new MachineData(mbbMachine);
		machineData.put(mbbLostPartFieldName, mbbLostPart);
		machineData.put(mbbPartCounterFieldName, mbbPartCounter);
		machineData.put(mbbManualModeFieldName, mbbManualMode);
		machineData.put(mbbTagAvailableFieldName, mbbTagAvailable);
		machineData.put(mbbPartAvailableFieldName, !mbbLostPart);
		machineData.put(mbbDoorOpenFieldName, mbbDoorOpen);
		// Data Message
		DataMessage dataMessage = new DataMessage();
		try {
			dataMessage.addMachineData(machineData, mbbMachineSchema);
		} catch (DataSchemaMismatchException e) {
			logger.error("There was a mismatch between the MBB schema and data");
		}
		// Send
		client.send(ContentType.JSON, mbbSchemaMessage);
		client.send(ContentType.JSON, dataMessage);
		// End
		logger.info("MBB data has been updated.");
	}
	
	private void setMBBLostPart(final HttpServerResponse response, boolean state) {
		logger.info("Setting lost part to " + state + " for MBB Scenario.");
		mbbLostPart = state;
		sendMBBData();
		response.end();
	}
	
	private void setMBBDoorOpen(final HttpServerResponse response, boolean state) {
		logger.info("Setting lost part to " + state + " for MBB Scenario.");
		mbbDoorOpen = state;
		sendMBBData();
		response.end();
	}
	
	private void setMBBManualMode(final HttpServerResponse response, boolean state) {
		logger.info("Setting lost part to " + state + " for MBB Scenario.");
		mbbManualMode = state;
		sendMBBData();
		response.end();
	}
	// End MBB Scenario
}
