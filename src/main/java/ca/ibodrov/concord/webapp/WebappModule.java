package ca.ibodrov.concord.webapp;

import com.google.inject.Binder;
import com.google.inject.Module;

import javax.inject.Named;
import javax.servlet.http.HttpServlet;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class WebappModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, HttpServlet.class).addBinding().to(SpaServlet.class).in(SINGLETON);
    }
}
