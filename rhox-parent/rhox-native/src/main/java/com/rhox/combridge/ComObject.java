/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.rhox.combridge;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.OaIdl.DISPID;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.AbstractJSObject;

/**
 * A Com-Node in a readable hierachy of values.<br/>
 * Note: This class is not threadsafe (which should be the default for most
 * com-objects anyway).
 */
@SuppressWarnings("restriction")
public class ComObject extends AbstractJSObject {

    private final ComObject parent;
    /**
     * Name of this Element.
     */
    private final String name;

    final Dispatcher dispatcher;

    /**
     * 'Remembers' internally which elements are used as properties and which
     * are methods.
     */
    private final Map<String, ComType> fieldTypes;

    /**
     * Creates a new JsComObject for the given com name.
     *
     * @param name the com name
     */
    public ComObject(String name) {
        this(null, name, new Dispatcher(name));
    }

    /**
     * Internal helper constructor.
     */
    private ComObject(ComObject parent, String name, Dispatcher dispatcher) {
        this(parent, name, dispatcher, new HashMap<>());
    }

    private ComObject(ComObject parent, String name, Dispatcher dispatcher, Map<String, ComType> fieldTypes) {
        this.parent = parent;
        this.name = name;
        this.dispatcher = dispatcher;
        this.fieldTypes = fieldTypes;
    }

    /**
     * Gets a property of this node.
     * <br/>
     * First, check if an element with this id exists. If it does not, throw an
     * exception. if it does, then read it. if this fails, then instead of
     * throwing an exception a new special sub-com-node is returned. this
     * comnode can be used to invoke a method all on the element later on.
     *
     * @param name member name
     * @return the member value
     */
    @Override
    public Object getMember(String name) {
        // Get id FIRST - if this operation fails then no field/ method parsing is necessary
        DISPID id = getId(name);

        ComType type = fieldTypes.get(name);
        if (ComType.METHOD.equals(type)) {
            return methodNode(name);
        }
        Variant.VARIANT v;
        try {
            v = dispatcher.get(id);
        } catch (COMException e) {
            return methodNode(name);
        }
        int vtype = v.getVarType().intValue();
        if (vtype == Variant.VT_EMPTY) {
            return methodNode(name);
        }
        Object result = toResult(name, v);
        setType(name, ComType.FIELD);
        return result;
    }

    /**
     * Creates a subinstance specially designed for invocations.
     */
    private ComObject methodNode(String name) {
        return new ComObject(this, name, null, fieldTypes);
    }

    /**
     * Sets the property of this node.
     *
     * @param name member name
     * @param value member value
     */
    @Override
    public void setMember(String name, Object value) {
        DISPID id = getId(name);
        VARIANT vValue = Variants.to(value);
        requireType(name, ComType.FIELD);
        try {
            dispatcher.set(id, vValue);
        } catch (COMException e) {
            throw newException(e);
        }
        setType(name, ComType.FIELD);
    }

    @Override
    public Object call(Object thiz, Object... args) {
        return invoke(args);
    }

    /**
     * Invokes the method on the current method-special node.
     *
     * @param args the parameters, will be converted to variants
     * @return the result of the invocation
     */
    public Object invoke(Object... args) {
        if (parent == null) {
            throw new IllegalStateException("Cannot invoke " + name + "(" + Arrays.toString(args) + ") on the Root COM element!");
        }
        // todo hier muss ich evtl felder gegen das parent auflösen können...
        DISPID id = parent.getId(name);
        VARIANT[] vArgs = Variants.toArray(args);
        // Wenn kein Dispatcher vorhanden ist, dann ist dies ein Method-Call
        boolean method = dispatcher == null;
        Variant.VARIANT v;
        try {
            v = parent.dispatcher.call(method, id, vArgs);
        } catch (COMException e) {
            throw newException(e);
        }
        Object result = toResult(name + "()", v);
        setType(name, ComType.METHOD);
        return result;
    }

    /**
     * Liefert den voll qualifizierten Pfad des Comobjects zurück.
     */
    private String getDesc() {
        List<String> path = new ArrayList<>();
        ComObject n = this;
        while (n != null) {
            path.add(n.name);
            n = n.parent;
        }
        Collections.reverse(path);
        return String.join(".", path);
    }

    @Override
    public String toString() {
        return "ComNode(" + getDesc() + ")";
    }

    private Object toResult(String name, Variant.VARIANT v) {
        Object o = Variants.from(v);
        if (o instanceof Dispatcher) {
            return new ComObject(this, name, (Dispatcher) o, fieldTypes);
        }
        return o;
    }

    private RuntimeException newException(COMException e) {
        return Variants.newException(getDesc(), e);
    }

    /**
     * Gets the display id for the given name or creates a new one.
     */
    private DISPID getId(String name) {
        try {
            return dispatcher.getId(name);
        } catch (COMException e) {
            throw newException(e);
        }
    }

    /**
     * Caches the id and and type, if they have not already been cached.
     */
    private void setType(String name, ComType type) {
        fieldTypes.putIfAbsent(name, type);
    }

    /**
     * Tests if this field with the given name is of the given type. Throws an
     * exception otherwise.
     */
    private void requireType(String name, ComType type) {
        ComType ctype = fieldTypes.get(name);
        if (ctype == null) {
            return;
        }
        if (ctype.equals(type)) {
            return;
        }
        throw new UnsupportedOperationException("Name '" + name + "' was already resolved as a "
                + ctype + " but required was a " + type + "!");
    }
}
