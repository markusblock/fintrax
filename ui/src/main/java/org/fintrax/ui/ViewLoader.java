package org.fintrax.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.fintrax.config.I18n;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

@Component
public class ViewLoader {
    private static final String FXML_ROOT = "/fxml/";

    private final ApplicationContext applicationContext;

    public ViewLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Parent load(String viewName) throws IOException {
        URL resource = ViewLoader.class.getResource(FXML_ROOT + viewName + ".fxml");
        if (resource == null) {
            throw new IOException("FXML resource not found: " + viewName);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        loader.setResources(I18n.getResourceBundle());
        loader.setControllerFactory(applicationContext::getBean);
        Parent root = loader.load();
        Object controller = loader.getController();
        if (controller != null) {
            root.getProperties().put(controller.getClass().getName(), controller);
        }
        return root;
    }

}
