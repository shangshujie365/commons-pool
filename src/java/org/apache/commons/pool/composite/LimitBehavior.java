/*
 * Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool.composite;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.KeyedObjectPool;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.NoSuchElementException;

/**
 * Specifies the behavior of when the max number of active objects has been reached.
 *
 * @see CompositeObjectPoolFactory#setLimitBehavior(LimitBehavior)
 * @see CompositeKeyedObjectPoolFactory#setLimitBehavior(LimitBehavior)
 * @author Sandy McArthur
 * @since #.#
 * @version $Revision$ $Date$
 */
public final class LimitBehavior implements Serializable {

    private static final long serialVersionUID = -4325661345028907604L;

    /**
     * When the number of active objects has been reached, fail with a <code>NoSuchElementException</code> instead of
     * returning a new object.
     * @see NoSuchElementException
     * @see ObjectPool#getNumActive
     * @see KeyedObjectPool#getNumActive
     */
    public static final LimitBehavior FAIL = new LimitBehavior("FAIL");

    /**
     * When the number of active objects has been reached, wait for the specified amount of time before failing with
     * a <code>NoSuchElementException</code>.
     * @see NoSuchElementException
     * @see ObjectPool#getNumActive
     * @see KeyedObjectPool#getNumActive
     */
    public static final LimitBehavior WAIT = new LimitBehavior("WAIT");

    /**
     * enum name.
     */
    private final String name;

    private LimitBehavior(final String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    // Autogenerated with Java 1.5 enums
    public static LimitBehavior[] values() {
        return new LimitBehavior[] {FAIL, WAIT};
    }

    // necessary for serialization
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private Object readResolve() throws ObjectStreamException {
        return values()[ordinal];
    }
}