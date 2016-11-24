package de.appsist.service.msd;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;

import de.appsist.commons.lang.LangUtil;
import de.appsist.commons.lang.StringBundle;

/**
 * Handler for web ui requets.
 * @author simon.schwantzer(at)im-c.de
 */
public class WebUIHandler {
	private static final Logger logger = LoggerFactory.getLogger(WebUIHandler.class);
	
	private final String basePath;
	private Vertx vertx;
	private Map<String, Template> templates; // Map with handlebars templates for HTML responses. 
	
	/**
	 * Creates the handler.
	 * @param basePath Base path for http requests. Is used templates are rendered.
	 * @param localFileHandler Handler to retrieve content list from.
	 * @param logger Logger for system information.
	 */
	public WebUIHandler(Vertx vertx, String basePath) {
		this.vertx = vertx;
		this.basePath = basePath;
		templates = new HashMap<>();
		try {
			TemplateLoader loader = new ClassPathTemplateLoader();
			loader.setPrefix("/templates");
			loader.setSuffix(".html");
			Handlebars handlebars = new Handlebars(loader);
			templates.put("control", handlebars.compile("control"));
		} catch (IOException e) {
			logger.fatal("Failed to load templates.", e);
		}
	}
	
	/**
	 * Resolves a request for a static file. 
	 * @param response Response to send file.
	 * @param filePath Path of the file to deliver.
	 */
	public void resolveStaticFileRequest(HttpServerResponse response, String filePath) {
		response.sendFile(filePath);
	}
	
	public void resolveControlRequest(HttpServerResponse response, String successId) {
		JsonObject data = new JsonObject();
		StringBundle bundle = LangUtil.getInstance(vertx.sharedData()).getBundle();

		data.putString("basePath", basePath);
		data.putString("title", bundle.getString("mid.ui.title", "APPsist Maschinenzustand-Simulationsdienst"));
		data.putString("header", bundle.getString("mid.ui.header", "Simulator für Maschinenzustände"));
		data.putString("festoscenario", bundle.getString("mid.ui.festoscenario", "Festo Pilot Szenario"));
		data.putString("fillloctite", bundle.getString("mid.ui.fillloctite", "Loctite füllen"));
		data.putString("emptyloctite", bundle.getString("mid.ui.emptyloctite", "Loctite leeren"));
		data.putString("fillfat", bundle.getString("mid.ui.fillfat", "Fett füllen"));
		data.putString("emptyfat", bundle.getString("mid.ui.emptyfat", "Fett leeren"));
		data.putString("reportweldseamerror", bundle.getString("mid.ui.reportweldseamerror", "Schweißnahtfehler erfassen"));
		data.putString("cebitscenario", bundle.getString("mid.ui.cebitscenario", "Festo Cebit Szenario"));
		data.putString("opendoor", bundle.getString("mid.ui.opendoor", "Tür öffnen"));
		data.putString("closedoor", bundle.getString("mid.ui.closedoor", "Tür schließen"));
		data.putString("emptyspringstorage", bundle.getString("mid.ui.emptyspringstorage", "Federmagazin leeren"));
		data.putString("fillspringstorage", bundle.getString("mid.ui.fillspringstorage", "Federmagazin füllen"));
		data.putString("emptycapstorage", bundle.getString("mid.ui.emptycapstorage", "Deckelmagazin leeren"));
		data.putString("fillcapstorage", bundle.getString("mid.ui.fillcapstorage", "Deckelmagazin füllen"));
		data.putString("floodingtests", bundle.getString("mid.ui.floodingtests", "Flooding Tests"));
		data.putString("hzon10", bundle.getString("mid.ui.hzon10", "10 Hz an"));
		data.putString("hzoff10", bundle.getString("mid.ui.hzoff10", "10 Hz aus"));
		data.putString("hzon100", bundle.getString("mid.ui.hzon100", "100 Hz an"));
		data.putString("hzoff100", bundle.getString("mid.ui.hzoff100", "100 Hz aus"));
		data.putString("mbbscenario", bundle.getString("msd.ui.mbbscenario", "MBB Szenario"));
		data.putString("lostpart", bundle.getString("mid.ui.lostpart", "Teil verloren"));
		data.putString("nopartlost", bundle.getString("mid.ui.nopartlost", "Kein Teil verloren"));
		data.putString("manualmode", bundle.getString("msd.ui.manualmode", "Handbetrieb"));
		data.putString("nomanualmode", bundle.getString("msd.ui.nomanualmode", "Automatikbetrieb"));
		data.putString("lock", bundle.getString("msd.ui.lock", "Verriegelung aktivieren"));
		data.putString("unlock", bundle.getString("msd.ui.unlock", "Verriegelung deaktivieren"));

		if (successId != null) {
			data.putString("successId", successId);
		}
		try {
			String html = templates.get("control").apply(data.toMap());
			response.end(html);
		} catch (IOException e) {
			response.setStatusCode(500); 
			response.end("Failed to render template.");
		}
	}
}
