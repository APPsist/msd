package de.appsist.service.msd;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import de.appsist.commons.event.StartupCompleteEvent;
import de.appsist.commons.misc.StatusSignalConfiguration;
import de.appsist.commons.misc.StatusSignalSender;

/**
 * Main verticle for the machine state simulation service. Its a software endpoint simulation a connected machine to be used for testing and demonstration purposes. 
 * @author simon.schwantzer(at)im-c.de
 */
public class MainVerticle extends Verticle {
	private Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	private JsonObject config;
	private RouteMatcher routeMatcher;
	private StateController stateController;
	private JsonObject webserverConfig;
	private JsonObject midConfig;
	private boolean useForMIDGUI;
	
	@Override
	public void start() {
		if (container.config() != null && container.config().size() > 0) {
			config = container.config();
		} else {
			logger.error("Warning: No configuration applied! Aborting.");
			System.exit(1);
		}
		
		webserverConfig = config.getObject("webserver");
		midConfig = config.getObject("mid");
		useForMIDGUI = midConfig.getBoolean("useForMIDGUI", false);
		
		stateController = new StateController(vertx, midConfig.getInteger("port"), webserverConfig.getBoolean("secure"), midConfig.getString("basePath"),
				config);
		
		initializeEventBusHandler();
		initializeHTTPRouting();
		HttpServer httpServer = vertx.createHttpServer();
		httpServer.requestHandler(routeMatcher);
		httpServer.listen(config.getObject("webserver").getInteger("port"));
		
		JsonObject statusSignalObject = config.getObject("statusSignal");
		StatusSignalConfiguration statusSignalConfig;
		if (statusSignalObject != null) {
		  statusSignalConfig = new StatusSignalConfiguration(statusSignalObject);
		} else {
		  statusSignalConfig = new StatusSignalConfiguration();
		}

		StatusSignalSender statusSignalSender =
		  new StatusSignalSender("msd", vertx, statusSignalConfig);
		statusSignalSender.start();

		
		logger.info("APPsist \"Machine State Simulation Service\" has been initialized.");
		
		if (useForMIDGUI) {
			logger.info("Use for GUI --> initializing RESTConnection");
			stateController.initializeRESTConnection();
			final SetMachineDataEventHandler setmachineDataEventHandler = new SetMachineDataEventHandler(vertx, midConfig.getInteger("port"), webserverConfig.getBoolean("secure"), midConfig.getString("basePath"));
			setmachineDataEventHandler.initializeRESTConnection();
		}
		
	}
	
	@Override
	public void stop() {
		logger.info("APPsist \"Machine State Simulation Service\" has been stopped.");
	}
	
	/**
	 * In this method the handlers for the event bus are initialized.
	 */
	private void initializeEventBusHandler() {
		vertx.eventBus().registerHandler(ProcessCompleteEventHandler.ADDRESS, new ProcessCompleteEventHandler(config.getObject("processes"), stateController));	
		final SetMachineDataEventHandler setmachineDataEventHandler = new SetMachineDataEventHandler(vertx, midConfig.getInteger("port"), webserverConfig.getBoolean("secure"), midConfig.getString("basePath"));
		vertx.eventBus().registerHandler(SetMachineDataEventHandler.ADDRESS, setmachineDataEventHandler);
		vertx.eventBus().registerHandler("appsist:event:" + StartupCompleteEvent.MODEL_ID, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				logger.info("Received StartupCompleteEvent --> initializing RESTConnection");
				stateController.initializeRESTConnection();
				setmachineDataEventHandler.initializeRESTConnection();
			}
		});
	}
	
	/**
	 * In this method the HTTP API build using a route matcher.
	 */
	private void initializeHTTPRouting() {
		final String basePath = config.getObject("webserver").getString("basePath");
		routeMatcher = new BasePathRouteMatcher(basePath);
		final WebUIHandler webUiHandler = new WebUIHandler(vertx, basePath);
		
		routeMatcher.get("/control", new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				String successId = request.params().get("success");
				webUiHandler.resolveControlRequest(request.response(), successId);
			}
		});
		
		// Lists the service configuration.
		routeMatcher.post("/performAction", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest request) {
				request.bodyHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						JsonObject body = new JsonObject(buffer.toString());
						stateController.performAction(body, request.response());
					}
				});
			}
		});
		
		routeMatcher.post("/performActionWithParam", new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest request) {
				request.bodyHandler(new Handler<Buffer>() {
					
					@Override
					public void handle(Buffer buffer) {
						JsonObject body = new JsonObject(buffer.toString());
						stateController.performActionWithParam(body, request.response());
					}
				});
			}
		});
		
		routeMatcher.getWithRegEx("/.+", new Handler<HttpServerRequest>() {
			
			@Override
			public void handle(HttpServerRequest request) {
				request.response().sendFile("www" + request.path().substring(basePath.length()));
			}
		});
	}
}
