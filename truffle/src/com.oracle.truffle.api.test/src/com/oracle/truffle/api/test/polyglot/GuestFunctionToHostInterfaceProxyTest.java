/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class GuestFunctionToHostInterfaceProxyTest extends AbstractPolyglotTest {

    @Before
    public void setup() {
        setupEnv();
    }

    @FunctionalInterface
    public interface BiFunctionLike<T, U, R> {
        <RR extends R, RRR extends RR> RRR apply(T t, U u);
    }

    @FunctionalInterface
    public interface BiFunctionBase<T, U, R> extends BiFunctionLike<T, U, R> {
    }

    @FunctionalInterface
    public interface BiFunctionReturningValue<T, U> extends BiFunctionBase<T, U, Value> {
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class JoinerFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) {
            return Arrays.stream(arguments).map(a -> a.toString()).collect(Collectors.joining(", "));
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class ArrayOfFunctionObject implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) {
            return new GuestArrayObject(arguments);
        }
    }

    @SuppressWarnings({"static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class GuestArrayObject implements TruffleObject {

        private final Object[] array;

        GuestArrayObject(Object[] array) {
            this.array = array;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return array.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException {
            if (!isArrayElementReadable(index)) {
                throw UnsupportedMessageException.create();
            }
            return array[(int) index];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < array.length;
        }
    }

    private static final TypeLiteral<BiFunctionLike<Object, Object, Object>> BF_OOO = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Value, Value, Value>> BF_VVV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, Value>> BF_OOV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends Value>> BF_OOXV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionReturningValue<Object, Object>> BFRV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ?>> BF_OOY = new TypeLiteral<>() {
    };

    private static final TypeLiteral<BiFunctionLike<Object, Object, List<Object>>> BF_OOLO = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<?>>> BF_OOLY = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<Value>>> BF_OOLV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, List<? extends Value>>> BF_OOLXV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends List<Value>>> BF_OOXLV = new TypeLiteral<>() {
    };
    private static final TypeLiteral<BiFunctionLike<Object, Object, ? extends List<? extends Value>>> BF_OOXLXV = new TypeLiteral<>() {
    };

    @Test
    public void testGenericReturnTypeOfPolyglotFunctionProxy() {
        Value join = context.asValue(new JoinerFunctionObject());
        assertThat(join.as(BF_OOO).apply("X", "Y"), equalTo("X, Y"));
        assertThat(join.as(BF_VVV).apply(context.asValue("X"), context.asValue("Y")), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BF_OOV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BF_OOXV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat(join.as(BFRV).apply("X", "Y"), isValueOfStringEqualTo("X, Y"));
        assertThat((Object) join.as(BF_OOY).apply("X", "Y"), equalTo("X, Y"));

        final var listOfStringsXandY = isListOf(equalTo("X"), equalTo("Y"));
        final var listOfValueOfStringsXandY = isListOf(isValueOfStringEqualTo("X"), isValueOfStringEqualTo("Y"));
        Value collect = context.asValue(new ArrayOfFunctionObject());
        assertThat(collect.as(BF_OOLO).apply("X", "Y"), listOfStringsXandY);
        assertThat(collect.as(BF_OOLY).apply("X", "Y"), listOfStringsXandY);
        assertThat(collect.as(BF_OOLV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOLXV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOXLV).apply("X", "Y"), listOfValueOfStringsXandY);
        assertThat(collect.as(BF_OOXLXV).apply("X", "Y"), listOfValueOfStringsXandY);
    }

    static Matcher<? super Value> isValueOfStringEqualTo(String expectedString) {
        class StringValueMatcher<T> extends BaseMatcher<T> {
            private final String expectedString;

            StringValueMatcher(String expectedString) {
                this.expectedString = expectedString;
            }

            @Override
            public boolean matches(Object item) {
                return item instanceof Value v && v.isString() && v.asString().equals(expectedString);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a Value of a String equal to ").appendValue(expectedString);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                super.describeMismatch(item, description);
                if (!(item instanceof Value v)) {
                    description.appendText(" (not a Value)");
                } else if (!v.isString()) {
                    description.appendText(" (a Value of a different type)");
                }
            }
        }
        return new StringValueMatcher<>(expectedString);
    }

    static Matcher<? super List<?>> isListOf(Matcher<?>... expectedValues) {
        class ListMatcher<T> extends BaseMatcher<T> {
            private final List<Matcher<?>> expectedValues;

            ListMatcher(List<Matcher<?>> expectedValues) {
                this.expectedValues = expectedValues;
            }

            @Override
            public boolean matches(Object item) {
                return item instanceof List<?> list &&
                                list.size() == expectedValues.size() &&
                                IntStream.range(0, list.size()).allMatch(i -> expectedValues.get(i).matches(list.get(i)));
            }

            @Override
            public void describeTo(Description description) {
                description.appendList("a List of [", ", ", "]", this.expectedValues);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof List<?> list) {
                    if (list.size() != expectedValues.size()) {
                        description.appendText("was a List of size ").appendValue(list.size()).appendText(", expected size ").appendValue(expectedValues.size());
                    } else {
                        description.appendText("[");
                        for (int i = 0; i < list.size(); i++) {
                            if (i != 0) {
                                description.appendText(", ");
                            }
                            description.appendText(i + ": ");
                            var matcher = expectedValues.get(i);
                            if (matcher.matches(list.get(i))) {
                                description.appendText("(matched)");
                            } else {
                                matcher.describeMismatch(list.get(i), description);
                            }
                        }
                        description.appendText("]");
                    }
                } else {
                    description.appendText("was not a List: ").appendValue(item);
                }
            }
        }
        return new ListMatcher<>(List.of(expectedValues));
    }
}
