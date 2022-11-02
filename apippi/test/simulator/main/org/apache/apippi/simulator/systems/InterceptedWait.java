/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.apippi.simulator.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.apippi.utils.Shared;
import org.apache.apippi.utils.concurrent.Condition;
import org.apache.apippi.utils.concurrent.Threads;
import org.apache.apippi.utils.concurrent.UncheckedInterruptedException;

import static org.apache.apippi.simulator.SimulatorUtils.failWithOOM;
import static org.apache.apippi.simulator.systems.InterceptedWait.CaptureSites.Capture.WAKE;
import static org.apache.apippi.simulator.systems.InterceptedWait.CaptureSites.Capture.WAKE_AND_NOW;
import static org.apache.apippi.simulator.systems.InterceptedWait.Trigger.SIGNAL;
import static org.apache.apippi.simulator.systems.InterceptibleThread.interceptorOrDefault;
import static org.apache.apippi.utils.Shared.Recursive.ALL;
import static org.apache.apippi.utils.Shared.Recursive.INTERFACES;
import static org.apache.apippi.utils.Shared.Scope.SIMULATION;

/**
 * A general abstraction for intercepted thread wait events, either
 * generated by the program execution or our nemesis system.
 */
@Shared(scope = SIMULATION, inner = INTERFACES)
public interface InterceptedWait extends NotifyThreadPaused
{
    enum Kind { SLEEP_UNTIL, WAIT_UNTIL, UNBOUNDED_WAIT, NEMESIS }
    enum Trigger { TIMEOUT, INTERRUPT, SIGNAL }

    interface TriggerListener
    {
        /**
         * Invoked when the wait is triggered, permitting any dependent Action to be invalidated.
         * This is particularly useful for thread timeouts, which are often logically invalidated
         * but may otherwise hold up scheduling of further events until their scheduled time passes.
         * @param triggered the wait that has been triggered, and is no longer valid
         */
        void onTrigger(InterceptedWait triggered);
    }

    /**
     * The kind of simulated wait
     */
    Kind kind();

    /**
     * true if the signal has already been triggered by another simulation action
     */
    boolean isTriggered();

    /**
     * true if this wait can be interrupted
     */
    boolean isInterruptible();

    /**
     * If kind() == TIMED_WAIT or ABSOLUTE_TIMED_WAIT this returns the relative and absolute
     * period to wait in nanos
     */
    long waitTime();

    /**
     * Intercept a wakeup signal on this wait
     */
    void interceptWakeup(Trigger trigger, Thread by);

    /**
     * Signal the waiter immediately, and have the caller wait until its simulation has terminated.
     *
     * @param interceptor the interceptor to relay events to
     * @param trigger if SIGNAL, propagate the signal to the wrapped condition we are waiting on
     */
    void triggerAndAwaitDone(InterceptorOfConsequences interceptor, Trigger trigger);

    /**
     * Signal all waiters immediately, bypassing the simulation
     */
    void triggerBypass();

    /**
     * Add a trigger listener, to notify the wait is no longer valid
     */
    void addListener(TriggerListener onTrigger);

    Thread waiting();

    /**
     * A general purpose superclass for implementing an intercepted/simulated thread wait event.
     * All share this implementation except for monitor waits, which must use the monitor they are waiting on
     * in order to release its lock.
     */
    class InterceptedConditionWait extends NotInterceptedSyncCondition implements InterceptedWait
    {
        static final Logger logger = LoggerFactory.getLogger(InterceptedConditionWait.class);

        final Kind kind;
        final InterceptibleThread waiting;
        final CaptureSites captureSites;
        final InterceptorOfConsequences interceptedBy;
        final Condition propagateSignal;
        final List<TriggerListener> onTrigger = new ArrayList<>(3);
        final long waitTime;
        boolean isInterruptible, isSignalPending, isTriggered, isDone, hasExited;

        public InterceptedConditionWait(Kind kind, long waitTime, InterceptibleThread waiting, CaptureSites captureSites, Condition propagateSignal)
        {
            this.kind = kind;
            this.waitTime = waitTime;
            this.waiting = waiting;
            this.captureSites = captureSites;
            this.interceptedBy = waiting.interceptedBy();
            this.propagateSignal = propagateSignal;
        }

        public synchronized void triggerAndAwaitDone(InterceptorOfConsequences interceptor, Trigger trigger)
        {
            if (isTriggered)
                return;

            if (hasExited)
            {
                logger.error("{} exited without trigger {}", waiting, captureSites == null ? new CaptureSites(waiting, WAKE_AND_NOW) : captureSites);
                throw failWithOOM();
            }

            waiting.beforeInvocation(interceptor, this);
            isTriggered = true;
            onTrigger.forEach(listener -> listener.onTrigger(this));

            if (!waiting.preWakeup(this) || !isInterruptible)
                super.signal();

            if (isSignalPending && propagateSignal != null)
                propagateSignal.signal();

            try
            {
                while (!isDone)
                    wait();
            }
            catch (InterruptedException ie)
            {
                throw new UncheckedInterruptedException(ie);
            }
        }

        public synchronized void triggerBypass()
        {
            if (isTriggered)
                return;

            isTriggered = true;
            super.signal();
            if (propagateSignal != null)
                propagateSignal.signal();
        }

        @Override
        public void addListener(TriggerListener onTrigger)
        {
            this.onTrigger.add(onTrigger);
        }

        @Override
        public Thread waiting()
        {
            return waiting;
        }

        @Override
        public synchronized void notifyThreadPaused()
        {
            isDone = true;
            notifyAll();
        }

        @Override
        public Kind kind()
        {
            return kind;
        }

        @Override
        public long waitTime()
        {
            return waitTime;
        }

        @Override
        public void interceptWakeup(Trigger trigger, Thread by)
        {
            assert !isTriggered;
            isSignalPending |= trigger == SIGNAL;
            if (captureSites != null)
                captureSites.registerWakeup(by);
            interceptorOrDefault(by).interceptWakeup(this, trigger, interceptedBy);
        }

        public boolean isTriggered()
        {
            return isTriggered;
        }

        @Override
        public boolean isInterruptible()
        {
            return isInterruptible;
        }

        // ignore return value; always false as can only represent artificial (intercepted) signaled status
        public boolean await(long time, TimeUnit unit) throws InterruptedException
        {
            try
            {
                isInterruptible = true;
                super.await();
            }
            finally
            {
                hasExited = true;
            }
            return false;
        }

        // ignore return value; always false as can only represent artificial (intercepted) signaled status
        public boolean awaitUntil(long until) throws InterruptedException
        {
            try
            {
                isInterruptible = true;
                super.await();
            }
            finally
            {
                hasExited = true;
            }
            return false;
        }

        // ignore return value; always false as can only represent artificial (intercepted) signaled status
        public boolean awaitUntilUninterruptibly(long until)
        {
            try
            {
                isInterruptible = false;
                super.awaitUninterruptibly();
            }
            finally
            {
                hasExited = true;
            }
            return false;
        }

        // ignore return value; always false as can only represent artificial (intercepted) signaled status
        public boolean awaitUninterruptibly(long time, TimeUnit units)
        {
            try
            {
                isInterruptible = false;
                super.awaitUninterruptibly(time, units);
            }
            finally
            {
                hasExited = true;
            }
            return false;
        }

        public Condition await() throws InterruptedException
        {
            try
            {
                isInterruptible = true;
                super.await();
            }
            finally
            {
                hasExited = true;
            }
            return this;
        }

        // declared as uninterruptible to the simulator to avoid unnecessary wakeups, but handles interrupts if they arise
        public Condition awaitDeclaredUninterruptible() throws InterruptedException
        {
            try
            {
                isInterruptible = false;
                super.await();
            }
            finally
            {
                hasExited = true;
            }
            return this;
        }

        public Condition awaitUninterruptibly()
        {
            try
            {
                isInterruptible = false;
                super.awaitUninterruptibly();
            }
            finally
            {
                hasExited = true;
            }
            return this;
        }

        public String toString()
        {
            return captureSites == null ? "" : "[" + captureSites + ']';
        }
    }

    // debug the place at which a thread waits or is signalled
    @Shared(scope = SIMULATION, members = ALL)
    public static class CaptureSites
    {
        public static class Capture
        {
            public static final Capture NONE = new Capture(false, false, false);
            public static final Capture WAKE = new Capture(true, false, false);
            public static final Capture WAKE_AND_NOW = new Capture(true, true, false);

            public final boolean waitSites;
            public final boolean wakeSites;
            public final boolean nowSites;

            public Capture(boolean waitSites, boolean wakeSites, boolean nowSites)
            {
                this.waitSites = waitSites;
                this.wakeSites = wakeSites;
                this.nowSites = nowSites;
            }

            public boolean any()
            {
                return waitSites | wakeSites | nowSites;
            }
        }

        final Thread waiting;
        final StackTraceElement[] waitSite;
        final Capture capture;
        @SuppressWarnings("unused") Thread waker;
        StackTraceElement[] wakeupSite;

        public CaptureSites(Thread waiting, StackTraceElement[] waitSite, Capture capture)
        {
            this.waiting = waiting;
            this.waitSite = waitSite;
            this.capture = capture;
        }

        public CaptureSites(Thread waiting, Capture capture)
        {
            this.waiting = waiting;
            this.waitSite = waiting.getStackTrace();
            this.capture = capture;
        }

        public CaptureSites(Thread waiting)
        {
            this.waiting = waiting;
            this.waitSite = waiting.getStackTrace();
            this.capture = WAKE;
        }

        public void registerWakeup(Thread waking)
        {
            this.waker = waking;
            if (capture.wakeSites)
                this.wakeupSite = waking.getStackTrace();
        }

        public String toString(Predicate<StackTraceElement> include)
        {
            String tail;
            if (wakeupSite != null)
                tail = Threads.prettyPrint(Stream.of(wakeupSite).filter(include), true, capture.nowSites ? "]# by[" : waitSite != null ? " by[" : "by[", "; ", "]");
            else if (capture.nowSites)
                tail = "]#";
            else
                tail = "";
            if (capture.nowSites)
                tail = Threads.prettyPrint(Stream.of(waiting.getStackTrace()).filter(include), true, waitSite != null ? " #[" : "#[", "; ", tail);
            if (waitSite != null)
                tail =Threads.prettyPrint(Stream.of(waitSite).filter(include), true, "", "; ", tail);
            return tail;
        }

        public String toString()
        {
            return toString(ignore -> true);
        }
    }

}
