/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

@EspressoSubstitutions
public final class Target_java_lang_System {

    public static final DebugCounter arraycopyCount = DebugCounter.create("arraycopyCount");
    static final DebugCounter identityHashCodeCount = DebugCounter.create("identityHashCodeCount");

    @Substitution
    public static int identityHashCode(@Host(Object.class) StaticObject self) {
        identityHashCodeCount.inc();
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(self));
    }

    @Substitution
    public static void arraycopy(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length) {
        arraycopyCount.inc();
        try {
            doArrayCopy(src, srcPos, dest, destPos, length);
        } catch (NullPointerException | ArrayStoreException | IndexOutOfBoundsException e) {
            // Should catch NPE if src or dest is null, and ArrayStoreException.
            throw EspressoLanguage.getCurrentContext().getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    private static void doArrayCopy(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length) {
        if (StaticObject.isNull(src) || StaticObject.isNull(dest)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NullPointerException.class);
        }
        // Mimics hotspot implementation.
        if (src.isArray() && dest.isArray()) {
            // System.arraycopy does the bounds checks
            if (src == dest) {
                // Same array, no need to type check
                boundsCheck(src, srcPos, dest, destPos, length);
                System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
            } else {
                Klass destType = dest.getKlass().getComponentType();
                Klass srcType = src.getKlass().getComponentType();
                if (destType.isPrimitive() && srcType.isPrimitive()) {
                    if (srcType != destType) {
                        throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayStoreException.class);
                    }
                    boundsCheck(src, srcPos, dest, destPos, length);
                    System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
                } else if (!destType.isPrimitive() && !srcType.isPrimitive()) {
                    if (destType.isAssignableFrom(srcType)) {
                        // We have guarantee we can copy, as all elements in src conform to dest.
                        boundsCheck(src, srcPos, dest, destPos, length);
                        System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
                    } else {
                        // slow path (manual copy) (/ex: copying an Object[] to a String[]) requires
                        // individual type checks. Should rarely happen ( < 1% of cases).
                        // @formatter:off
                        // Use cases:
                        // - System startup.
                        // - MethodHandle and CallSite linking.
                        boundsCheck(src, srcPos, dest, destPos, length);
                        StaticObject[] s = src.unwrap();
                        StaticObject[] d = dest.unwrap();
                        for (int i = 0; i < length; i++) {
                            StaticObject cpy = s[i + srcPos];
                            if (StaticObject.isNull(cpy) || destType.isAssignableFrom(cpy.getKlass())) {
                                d[destPos + i] = cpy;
                            } else {
                                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayStoreException.class);
                            }
                        }
                    }
                } else {
                    throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayStoreException.class);
                }
            }
        } else {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayStoreException.class);
        }
    }

    private static void boundsCheck(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length) {
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos > src.length() - length || destPos > dest.length() - length) {
            // Other checks are caught during execution without side effects.
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class);
        }
    }

    @Substitution
    public static void exit(int code) {
        throw new EspressoExitException(code);
    }
}
