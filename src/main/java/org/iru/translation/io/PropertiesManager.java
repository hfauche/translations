package org.iru.translation.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.iru.translation.DictionnaryManager;
import org.iru.translation.TranslationException;
import org.iru.translation.gui.Action;
import org.iru.translation.model.PropertyTableModel;
import org.iru.translation.model.PropertyTableModel.Property;

public class PropertiesManager {
    
    private final DictionnaryManager dictionnaryManager;
    
    public PropertiesManager(DictionnaryManager dictionnaryManager) {
        this.dictionnaryManager = dictionnaryManager;
    }
    
    public Properties readProperties(File f) throws TranslationException {
        Properties p = new Properties();
        p.load(f);
        return p;
    }
    
    public List<Property> loadProperties(Properties props) {
        return props.entrySet().stream()
            .filter(e -> (!dictionnaryManager.isKeyInDictionnary(e.getKey()) && !dictionnaryManager.isValueInDictionnary(e.getValue().getValue())))
            .sorted((e1, e2) -> {return e1.getKey().compareToIgnoreCase(e2.getKey());})
            .map(e -> new PropertyTableModel.Property(e.getKey(), escapeLineBreaks(e.getValue().getValue()), null, Action.NONE))
            .collect(Collectors.toList());
    }

    public List<Property> diff(Properties fromProps, Properties toProps) {
        List<Property> result = new LinkedList<>();
        Set<Object> insertedkeys = new HashSet<>(fromProps.size());
        fromProps.entrySet().stream()
            .filter(e -> (!dictionnaryManager.isKeyInDictionnary(e.getKey()) && !dictionnaryManager.isValueInDictionnary(e.getValue().getValue())))
            .forEach(e -> {
                final org.iru.translation.io.Property toProp = toProps.get(e.getKey());
                String toValueAsString = toProp !=null ? toProp.getValue() : null;
                final String fromValueAsString = e.getValue().getValue();
                if (toValueAsString == null) {
                    result.add(new Property(e.getKey(), escapeLineBreaks(fromValueAsString), null, Action.MISSING));
                } else if (fromValueAsString.trim().equalsIgnoreCase(toValueAsString.trim())) {
                    result.add(new Property(e.getKey(), escapeLineBreaks(fromValueAsString), escapeLineBreaks(toValueAsString), Action.UNTRANSLATED));
                } else if (!insertedkeys.contains(e.getKey())) {
                    insertedkeys.add(e.getKey());
                    result.add(new Property(e.getKey(), escapeLineBreaks(fromValueAsString), escapeLineBreaks(toValueAsString), Action.NONE));
                }
            });
        toProps.entrySet().stream()
            .filter(e -> (!dictionnaryManager.isKeyInDictionnary(e.getKey()) && !dictionnaryManager.isValueInDictionnary(e.getValue().getValue())))
            .forEach(e -> {
                final org.iru.translation.io.Property fromProp = fromProps.get(e.getKey());
                String fromValueAsString = fromProp !=null ? fromProp.getValue() : null;
                if (fromValueAsString == null) {
                    result.add(new Property(e.getKey(), null, escapeLineBreaks(e.getValue().getValue()), Action.OBSOLETE));
                }
            });
        Collections.sort(result);
        return result;
    }
    
    public void exportToCsv(PropertyTableModel tableModel) throws TranslationException {
        String newLine = System.getProperty("line.separator");
        File f = new File(System.getProperty("java.io.tmpdir") + "/translations-export.csv"); 
        try (FileWriter fw = new FileWriter(f)) {
            for (int i=0; i<tableModel.getRowCount(); i++) {
                final Property prop = tableModel.getModel(i);
                fw.append(prop.getKey()).append(';');
                if (prop.getValueFrom() != null) {
                    fw.append(prop.getValueFrom()).append(';');
                }
                if (prop.getValueTo() != null) {
                    fw.append(prop.getValueTo()).append(';');
                }
                fw.append(newLine);
            }
        } catch(Exception ex) {
            throw new TranslationException("Impossible to export data", ex);
        }
    }

    public void importFromCsv(Properties toProps, File f) throws TranslationException {
        final FileReader fileReader;
        try {
            fileReader = new FileReader(f);
        } catch (FileNotFoundException ex) {
            throw new TranslationException("File to import not found", ex);
        }
        LineNumberReader is = new LineNumberReader(fileReader);
        Map<String, String> props = new HashMap<>();
        try {
            String line = is.readLine();
            while (line != null) {
                String[] prop = line.split(";");
                if (prop.length != 2) {
                    throw new TranslationException("Format error at line " + is.getLineNumber());
                }
                String key = prop[0].trim();
                String value = prop[1].trim();
                props.put(key, value);
                line = is.readLine();
            }
        }
        catch (IOException ex) {
            throw new TranslationException("Error when reading CSV file", ex);
        }
        props.forEach((k, v) -> toProps.set(k, v));
    }

    public void save(Properties toProps, Properties fromProps, PropertyTableModel model, File f) throws TranslationException {
        for (int i=0; i<model.getRowCount(); i++) {
            Property p = model.getModel(i);
            String valueTo = p.getValueTo();
            if (valueTo != null) {
                valueTo = unEscapeLineBreaks(valueTo.trim());
            }
            switch (p.getAction()) {
                case UNTRANSLATED:
                case NONE:
                    toProps.get(p.getKey()).setValue(valueTo);
                    break;
                case OBSOLETE:
                    if (valueTo == null || valueTo.isEmpty()) {
                        toProps.remove(toProps.get(p.getKey()).getPos());
                    } else {
                        toProps.get(p.getKey()).setValue(valueTo);
                    }
                    break;
                case MISSING:
                    if (valueTo != null && !valueTo.isEmpty()) {
                        toProps.set(p.getKey(), valueTo, fromProps.get(p.getKey()).getPos());
                    }
                    break;
            }
        }
        try(FileWriter fw = new FileWriter(f)) {
            toProps.store(fw);
        } catch (TranslationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TranslationException("Unable to save properties file " + f.getName(), ex);
        }
    }
    
    public static String escapeLineBreaks(String value) {
        if (value == null) return null;
        return value.replace("\\n", "<LINEBREAK>");
    }

    public static String unEscapeLineBreaks(String value) {
        if (value == null) return null;
        return value.replace("<LINEBREAK>", "\\n");
    }
}
