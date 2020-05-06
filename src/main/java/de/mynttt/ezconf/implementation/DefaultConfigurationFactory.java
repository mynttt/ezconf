package de.mynttt.ezconf.implementation;

import de.mynttt.ezconf.Configuration;
import de.mynttt.ezconf.ConfigurationFactory;
import de.mynttt.ezconf.ConfigurationGroup;

/**
 * Factory for the reference implementation.
 * 
 * @author mynttt
 *
 */
public final class DefaultConfigurationFactory implements ConfigurationFactory {

    @Override
    public ConfigurationGroup newConfigurationGroup(String identifier) {
        return new DefaultConfigurationGroup(identifier);
    }

    @Override
    public Configuration newConfiguration() {
        return new DefaultConfiguration();
    }

}
