package ca.ibodrov.concord.webapp;

import com.google.inject.Binder;
import com.google.inject.Module;

import javax.inject.Named;
import javax.servlet.Filter;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class WebappPluginModule implements Module {

    @Override
    public void configure(Binder binder) {
        newSetBinder(binder, Filter.class).addBinding().to(WebappFilter.class);
    }
}
