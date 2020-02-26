package com.inspiring.surf.integration.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.inspiring.surf.integration.listeners.FileListener;
import com.inspiring.surf.integration.listeners.PropertyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Component
public class DynamicProperties implements FileListener {

    private static Logger log = LoggerFactory.getLogger(DynamicProperties.class);

    private static Map<String, String> propertiesCache = new HashMap<>();
    private static Map<String, List<PropertyListener>> regularListeners = new HashMap<>();
    private static Map<String, List<PropertyListener>> specialListeners = new HashMap<>();

    @Autowired
    private ResourceLoader resourceLoader;

    private static final String CONFIG_FILE_PATH = "classpath:application.properties";

    @PostConstruct
    public void start() throws IOException {
        log.debug("Starting Dynamic Properties");
        FileMonitor fileMonitor = FileMonitor.getInstance();
        fileMonitor.addFileListener(this, getConfigFile(), 5000L);
        load();
    }

    @PreDestroy
    public void stop() throws IOException {
        FileMonitor fileMonitor = FileMonitor.getInstance();
        fileMonitor.removeFileListener(this, getConfigFile());
    }

    public void registerListener(String key, PropertyListener listener) {
        log.debug("Registering listener to property: {}", key);
        if (key.contains("*")) {
            key = key.replace("*", "");
            if (!specialListeners.containsKey(key)) {
                specialListeners.put(key, new ArrayList<>());
            }
            specialListeners.get(key).add(listener);
        } else {
            if (!regularListeners.containsKey(key)) {
                regularListeners.put(key, new ArrayList<>());
            }
            regularListeners.get(key).add(listener);
        }
    }

    public void unregisterListener(String key, PropertyListener listener) {
        if (key.contains("*")) {
            key = key.replace("*", "");
            if (specialListeners.containsKey(key)) {
                specialListeners.get(key).remove(listener);
            }
        } else {
            if (regularListeners.containsKey(key)) {
                regularListeners.get(key).remove(listener);
            }
        }
    }

    public Map<String, String> getMapValues(String like) {

        Map<String, String> subMap = new HashMap<>();

        for (String key : propertiesCache.keySet()) {
            if (key.contains(like)) {
                subMap.put(key, propertiesCache.get(key));
            }
        }
        return subMap;
    }

    public Long getLong(String propertyName) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return null;
        } else {
            try {
                return Long.valueOf(propertiesCache.get(propertyName));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public Long getLong(String propertyName, long defaultValue) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return defaultValue;
        } else {
            try {
                return Long.valueOf(propertiesCache.get(propertyName));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public Integer getInteger(String propertyName) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return null;
        } else {
            try {
                return Integer.valueOf(propertiesCache.get(propertyName));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public Integer getInteger(String propertyName, int defaultValue) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return defaultValue;
        } else {
            try {
                return Integer.valueOf(propertiesCache.get(propertyName));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public Boolean getBoolean(String propertyName) {
        if (isEmpty(propertyName)) {
            return null;
        } else {
            return Boolean.parseBoolean(propertiesCache.get(propertyName));
        }
    }

    public Boolean getBoolean(String propertyName, boolean defaultValue) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(propertiesCache.get(propertyName));
        }
    }

    public String getString(String propertyName) {
        if (isEmpty(propertyName)) {
            return null;
        } else {
            return propertiesCache.get(propertyName);
        }
    }

    public String getString(String propertyName, String defaultValue) {
        if (isEmpty(propertyName) || !propertiesCache.containsKey(propertyName)) {
            return defaultValue;
        } else {
            return propertiesCache.get(propertyName);
        }
    }

    private void notifyListeners(String paramName, String value) {
        if (regularListeners.containsKey(paramName)) {
            for (PropertyListener listener : regularListeners.get(paramName)) {
                listener.notify(paramName, value);
            }
        }

        for (String key : specialListeners.keySet()) {
            if (paramName.contains(key)) {
                for (PropertyListener listener : specialListeners.get(key)) {
                    listener.notify(paramName, value);
                }
            }
        }
    }

    @Override
    public void fileChanged(File fileName) {
        load();
    }

    private void load() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(getConfigFile()));
            for (String key : prop.stringPropertyNames()) {
                if (propertiesCache.containsKey(key)) {
                    if (!propertiesCache.get(key).equals(prop.get(key))) {
                        log.debug("Dynamic Properties - Property Updated: {}, Value: {}", key, prop.getProperty(key));
                        propertiesCache.put(key, prop.getProperty(key));
                        notifyListeners(key, propertiesCache.get(key));
                    }
                } else {
                    log.debug("Dynamic Properties - Property Added: {}, Value: {}", key, prop.getProperty(key));
                    propertiesCache.put(key, prop.getProperty(key));
                    notifyListeners(key, propertiesCache.get(key));
                }
            }
        } catch (Exception e) {
            log.error("Error reading configuration file: {}", CONFIG_FILE_PATH);
        }
    }

    private File getConfigFile() throws IOException {
        Resource file = resourceLoader.getResource(CONFIG_FILE_PATH);
        return file.getFile();
    }
}
