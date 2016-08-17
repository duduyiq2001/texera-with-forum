package edu.uci.ics.textdb.common.field;

import edu.uci.ics.textdb.api.common.IField;

/**
 * Created by chenli on 3/31/16. A field that is indexed but not tokenized: the
 * entire String value is indexed as a single token. For example this might be
 * used for a 'country' field or an 'id' field, or any field that you intend to
 * use for sorting or access through the field cache.
 */
public class StringField implements IField {

    private final String value;


    public StringField(String value) {
        this.value = value;
    }


    public String getValue() {
        return value;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StringField that = (StringField) o;

        return value != null ? value.equals(that.value) : that.value == null;

    }


    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }


    @Override
    public String toString() {
        return "StringField [value=" + value + "]";
    }

}
