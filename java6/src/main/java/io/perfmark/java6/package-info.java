/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This package includes implementations of {@link io.perfmark.impl.Generator} and {@link io.perfmark.impl.MarkRecorder}
 * that are compiled for Java 6.  These implementations allow running PerfMark on platforms where later Java features
 * may not be available.  To use this package, add a runtime dependency to your project on this jar, and PerfMark will
 * automatically attempt to use it.   If more modern implementations are available, PerfMark may attempt to use those
 * instead.
 */
package io.perfmark.java6;
