/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.elements.fields;

import com.manydesigns.elements.logging.LogUtil;
import com.manydesigns.elements.reflection.ClassAccessor;
import com.manydesigns.elements.reflection.JavaClassAccessor;
import com.manydesigns.elements.reflection.PropertyAccessor;
import com.manydesigns.elements.text.TextFormat;
import com.manydesigns.elements.util.Util;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.ArrayUtils;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class DefaultOptionProvider implements OptionProvider {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    //**************************************************************************
    // Fields
    //**************************************************************************

    protected final String name;
    protected final int fieldCount;
    protected final Object[] values;
    protected final String[] labelSearch;
    protected final Object[][] valuesArray;
    protected final String[][] labelsArray;
    protected final Map<Object, String>[] optionsArray;
    protected boolean needsValidation;
    protected boolean autoconnect;

    public final static Logger logger =
            LogUtil.getLogger(DefaultOptionProvider.class);
    public static final String NON_WORD_CHARACTERS = " \t\n\f\r\\||!\"£$%&/()='?^[]+*@#<>,;.:-_";

    //**************************************************************************
    // Static builders
    //**************************************************************************

    public static DefaultOptionProvider create(String name,
                                               int fieldCount,
                                               Object[] values,
                                               String[] labels) {
        Object[][] valuesArray = new Object[values.length][];
        for (int i = 0; i < values.length; i++) {
            valuesArray[i] = new Object[1];
            valuesArray[i][0] = values[i];
        }

        String[][] labelsArray = new String[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            labelsArray[i] = new String[1];
            labelsArray[i][0] = labels[i];
        }

        return create(name, fieldCount, valuesArray, labelsArray);
    }


    public static DefaultOptionProvider create(String name,
                                               int fieldCount,
                                               Object[][] valuesArray,
                                               String[][] labelsArray) {
        return new DefaultOptionProvider(name, fieldCount,
                valuesArray, labelsArray);
    }

    public static DefaultOptionProvider create(String name,
                                               Collection<Object> objects,
                                               ClassAccessor classAccessor,
                                               TextFormat textFormat) {
        PropertyAccessor[] keyProperties = classAccessor.getKeyProperties();
        return create(name, objects, textFormat, keyProperties);
    }

    protected static DefaultOptionProvider create(String name,
                                                  Collection<Object> objects,
                                                  Class objectClass,
                                                  TextFormat textFormat,
                                                  String... propertyNames) {
        ClassAccessor classAccessor =
                JavaClassAccessor.getClassAccessor(objectClass);
        PropertyAccessor[] propertyAccessors =
                new PropertyAccessor[propertyNames.length];
        for (int i = 0; i < propertyNames.length; i++) {
            String currentName = propertyNames[i];
            try {
                PropertyAccessor propertyAccessor =
                        classAccessor.getProperty(currentName);
                propertyAccessors[i] = propertyAccessor;
            } catch (Throwable e) {
                String msg = MessageFormat.format(
                        "Could not access property: {0}", currentName);
                LogUtil.warning(logger, msg, e);
                throw new IllegalArgumentException(msg, e);
            }
        }
        return create(name, objects, textFormat, propertyAccessors);
    }

    protected static DefaultOptionProvider create(
            String name,
            Collection<Object> objects,
            TextFormat textFormat,
            PropertyAccessor... propertyAccessors
    ) {
        int fieldsCount = propertyAccessors.length;
        Object[][] valuesArray = new Object[objects.size()][fieldsCount];
        String[][] labelsArray = new String[objects.size()][fieldsCount];
        int i = 0;
        for (Object current : objects) {
            Object[] values = new Object[fieldsCount];
            String[] labels = new String[fieldsCount];
            int j = 0;
            String shortName = null;
            if (textFormat != null) {
                shortName = textFormat.format(current);
            }
            for (PropertyAccessor property : propertyAccessors) {
                try {
                    Object value = property.get(current);
                    values[j] = value;
                    if (textFormat == null) {
                        String label =
                                (String) Util.convertValue(value, String.class);
                        labels[j] = label;
                    } else {
                        labels[j] = shortName;
                    }
                } catch (Throwable e) {
                    String msg = MessageFormat.format(
                            "Could not access property: {0}",
                            property.getName());
                    LogUtil.warning(logger, msg, e);
                    throw new IllegalArgumentException(msg, e);
                }
                j++;
            }
            valuesArray[i] = values;
            labelsArray[i] = labels;
            i++;
        }
        return new DefaultOptionProvider(
                name, fieldsCount, valuesArray, labelsArray);
    }

    //**************************************************************************
    // Constructor
    //**************************************************************************

    protected DefaultOptionProvider(String name,
                                    int fieldCount,
                                    Object[][] valuesArray,
                                    String[][] labelsArray) {
        this.name = name;
        this.fieldCount = fieldCount;
        this.valuesArray = valuesArray;
        this.labelsArray = labelsArray;
        values = new Object[fieldCount];
        labelSearch = new String[fieldCount];
        //noinspection unchecked
        optionsArray = new Map[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            optionsArray[i] = new LinkedHashMap<Object, String>();
        }
        needsValidation = true;
    }


    //**************************************************************************
    // OptionProvider implementation
    //**************************************************************************

    public String getName() {
        return name;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public void setValue(int index, Object value) {
        values[index] = value;
        needsValidation = true;
    }

    public Object getValue(int index) {
        validate();
        return values[index];
    }

    public void setLabelSearch(int index, String value) {
        labelSearch[index] = StringUtils.trimToNull(value);
        needsValidation = true;
    }

    public String getLabelSearch(int index) {
        return labelSearch[index];
    }

    public Map<Object, String> getOptions(int index) {
        validate();
        return optionsArray[index];
    }

    public boolean isAutoconnect() {
        return autoconnect;
    }

    public void setAutoconnect(boolean autoconnect) {
        this.autoconnect = autoconnect;
    }

    //**************************************************************************
    // inetrnal-use methods
    //**************************************************************************


    protected void validate() {
        if (!needsValidation) {
            return;
        }

        needsValidation = false;
        resetOptionsArray();

        // normalize null in values (only null values after first null)
        boolean foundNull = false;
        for (int j = 0; j < fieldCount; j++) {
            if (foundNull) {
                values[j] = null;
            } else if (values[j] == null) {
                foundNull = true;
            }
        }

        int maxMatchingIndex = -1;
        for (int i = 0; i < valuesArray.length; i++) {
            Object[] currentValueRow = valuesArray[i];
            String[] currentLabelRow = labelsArray[i];
            boolean matching = true;
            for (int j = 0; j < fieldCount; j++) {
                Object cellValue = currentValueRow[j];
                String cellLabel = currentLabelRow[j];
                Object value = values[j];
                String labelSearch2 = labelSearch[j];

                Map<Object, String> options = optionsArray[j];
                if (matching && matchLabel(cellLabel, labelSearch2)) {
                    options.put(cellValue, cellLabel);
                }

                if (matching && value != null
                        && value.equals(cellValue)) {
                    if (j > maxMatchingIndex) {
                        maxMatchingIndex = j;
                    }
                } else {
                    matching = false;
                }
            }
        }

        for (int i = maxMatchingIndex + 1; i < fieldCount; i++) {
            values[i] = null;
        }
    }

    private boolean matchLabel(String cellLabel, String labelSearch2) {
        if (labelSearch2 == null || labelSearch2.length() == 0) {
            return true;
        }
        cellLabel = cellLabel.toLowerCase();
        labelSearch2 = labelSearch2.toLowerCase();
        String[] cellLabelArray =
                StringUtils.split(cellLabel, NON_WORD_CHARACTERS);
        for (String current : cellLabelArray) {
            if (current.startsWith(labelSearch2)) {
                return true;
            }
        }
        return false;
    }

    public void resetOptionsArray() {
        for (int i = 0; i < fieldCount; i++) {
            optionsArray[i].clear();
        }
    }
}
