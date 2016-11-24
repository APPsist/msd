package de.appsist.service.msd;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import de.appsist.commons.event.ProcessCompleteEvent;
import de.appsist.commons.util.EventUtil;

/**
 * Handler for process completion messages.
 * @author simon.schwantzer(at)im-c.de
 */
public class ProcessCompleteEventHandler implements Handler<Message<JsonObject>> {
	private static final Logger logger = LoggerFactory.getLogger(ProcessCompleteEventHandler.class);
	
	public static String ADDRESS = "appsist:event:" + ProcessCompleteEvent.MODEL_ID;
	
	private StateController stateController;
	
	private final String loctiteProcessId;
	private final String fatProcessId;
	
	public ProcessCompleteEventHandler(JsonObject processConfig, StateController stateController) {
		this.stateController = stateController;
		loctiteProcessId = processConfig.getObject("loctite-wechseln").getString("processId");
		fatProcessId = processConfig.getObject("fett-wechseln").getString("processId");
	}
	
	@Override
	public void handle(Message<JsonObject> message) {
		ProcessCompleteEvent event = EventUtil.parseEvent(message.body().toMap(), ProcessCompleteEvent.class);
		if (event.getProcessId().equals(loctiteProcessId)) {
			stateController.festoPilotSetLoctite(true);
			logger.info("The loctite process has been completed.");
		} else if (event.getProcessId().equals(fatProcessId)) {
			stateController.festoPilotSetFat(true);
			logger.info("The fat process has been completed.");
		}
	}
}