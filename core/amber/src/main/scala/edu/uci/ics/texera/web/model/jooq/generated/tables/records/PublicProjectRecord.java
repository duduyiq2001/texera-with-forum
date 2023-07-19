/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.web.model.jooq.generated.tables.records;


import edu.uci.ics.texera.web.model.jooq.generated.tables.PublicProject;
import edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces.IPublicProject;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Row1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class PublicProjectRecord extends UpdatableRecordImpl<PublicProjectRecord> implements Record1<UInteger>, IPublicProject {

    private static final long serialVersionUID = 429243747;

    /**
     * Setter for <code>texera_db.public_project.pid</code>.
     */
    @Override
    public void setPid(UInteger value) {
        set(0, value);
    }

    /**
     * Getter for <code>texera_db.public_project.pid</code>.
     */
    @Override
    public UInteger getPid() {
        return (UInteger) get(0);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<UInteger> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record1 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row1<UInteger> fieldsRow() {
        return (Row1) super.fieldsRow();
    }

    @Override
    public Row1<UInteger> valuesRow() {
        return (Row1) super.valuesRow();
    }

    @Override
    public Field<UInteger> field1() {
        return PublicProject.PUBLIC_PROJECT.PID;
    }

    @Override
    public UInteger component1() {
        return getPid();
    }

    @Override
    public UInteger value1() {
        return getPid();
    }

    @Override
    public PublicProjectRecord value1(UInteger value) {
        setPid(value);
        return this;
    }

    @Override
    public PublicProjectRecord values(UInteger value1) {
        value1(value1);
        return this;
    }

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    @Override
    public void from(IPublicProject from) {
        setPid(from.getPid());
    }

    @Override
    public <E extends IPublicProject> E into(E into) {
        into.from(this);
        return into;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached PublicProjectRecord
     */
    public PublicProjectRecord() {
        super(PublicProject.PUBLIC_PROJECT);
    }

    /**
     * Create a detached, initialised PublicProjectRecord
     */
    public PublicProjectRecord(UInteger pid) {
        super(PublicProject.PUBLIC_PROJECT);

        set(0, pid);
    }
}