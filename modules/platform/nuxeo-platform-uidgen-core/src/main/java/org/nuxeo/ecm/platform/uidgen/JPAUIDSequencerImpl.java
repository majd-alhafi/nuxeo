/*
 * (C) Copyright 2006-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.ecm.platform.uidgen;

import static org.nuxeo.common.concurrent.ThreadFactories.newThreadFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.persistence.PersistenceProvider;
import org.nuxeo.ecm.core.persistence.PersistenceProvider.RunCallback;
import org.nuxeo.ecm.core.persistence.PersistenceProviderFactory;
import org.nuxeo.ecm.core.uidgen.AbstractUIDSequencer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * This implementation uses a static persistence provider to be able to instantiate this class without passing by
 * Framework.getService -&gt; this is to avoid potential problems do to sequencer factories. Anyway sequencer factories
 * should be removed (I don't think they are really needed).
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class JPAUIDSequencerImpl extends AbstractUIDSequencer {

    public static final int POOL_SIZE = 1;

    public static final int MAX_POOL_SIZE = 2;

    public static final long KEEP_ALIVE_TIME = 10L;

    public static final int QUEUE_SIZE = 1000;

    private static volatile PersistenceProvider persistenceProvider;

    protected ThreadPoolExecutor tpe;

    public JPAUIDSequencerImpl() {
    }

    @Override
    public void init() {
        if (tpe != null && !tpe.isShutdown()) {
            tpe.shutdownNow();
        }
        tpe = new ThreadPoolExecutor(POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE), newThreadFactory("jpa-uid-sequencer"));
    }

    /**
     * Must be called when the service is no longer needed
     */
    @Override
    public void dispose() {
        deactivatePersistenceProvider();
        tpe.shutdownNow();
    }

    protected PersistenceProvider getOrCreatePersistenceProvider() {
        if (persistenceProvider == null) {
            synchronized (JPAUIDSequencerImpl.class) {
                if (persistenceProvider == null) {
                    activatePersistenceProvider();
                }
            }
        }
        return persistenceProvider;
    }

    protected static void activatePersistenceProvider() {
        Thread thread = Thread.currentThread();
        ClassLoader last = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(PersistenceProvider.class.getClassLoader());
            PersistenceProviderFactory persistenceProviderFactory = Framework.getService(
                    PersistenceProviderFactory.class);
            persistenceProvider = persistenceProviderFactory.newProvider("NXUIDSequencer");
            persistenceProvider.openPersistenceUnit();
        } finally {
            thread.setContextClassLoader(last);
        }
    }

    private static void deactivatePersistenceProvider() {
        if (persistenceProvider != null) {
            synchronized (JPAUIDSequencerImpl.class) {
                if (persistenceProvider != null) {
                    persistenceProvider.closePersistenceUnit();
                    persistenceProvider = null;
                }
            }
        }
    }

    protected static class SeqRunnable implements Runnable {

        protected final String key;

        protected final ToIntFunction<String> runner;

        protected int result;

        protected SeqRunnable(String key, ToIntFunction<String> runner) {
            if (StringUtils.isBlank(key)) {
                throw new IllegalArgumentException("The key cannot be null or empty");
            }
            this.key = key;
            this.runner = runner;
        }

        @Override
        public void run() {
            TransactionHelper.startTransaction();
            try {
                result = runner.applyAsInt(key);
            } finally {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }

        public int getResult() {
            return result;
        }

    }

    @Override
    public void initSequence(String key, long id) {
        while (getNextLong(key) < id) {
            continue;
        }
    }

    @Override
    public List<String> getKeys() {
        return getOrCreatePersistenceProvider().run(true,
                (RunCallback<List<String>>) em -> em.createQuery("from UIDSequenceBean seq", UIDSequenceBean.class)
                                                    .getResultStream()
                                                    .map(UIDSequenceBean::getKey)
                                                    .toList());
    }

    @Override
    public long getCurrent(String key) {
        return executeSeqRunnable(new SeqRunnable(key, this::doGet));
    }

    @Override
    public long getNextLong(final String key) {
        return executeSeqRunnable(new SeqRunnable(key, this::doGetNext));
    }

    protected long executeSeqRunnable(SeqRunnable runnable) {
        Future<?> future = tpe.submit(runnable);
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NuxeoException nuxeoException) {
                throw nuxeoException;
            }
            throw new NuxeoException(e.getCause());
        }
        return runnable.getResult();
    }

    @SuppressWarnings("boxing")
    protected int doGet(final String key) {
        return getOrCreatePersistenceProvider().run(true, (RunCallback<Integer>) em -> get(em, key));
    }

    protected int get(EntityManager em, String key) {
        UIDSequenceBean seq;
        try {
            seq = em.createNamedQuery("UIDSequence.findByKey", UIDSequenceBean.class)
                    .setParameter("key", key)
                    .getSingleResult();
        } catch (NoResultException e) {
            return (int) SEQUENCE_DOES_NOT_EXIST;
        }
        return seq.getIndex();
    }

    @SuppressWarnings("boxing")
    protected int doGetNext(final String key) {
        return getOrCreatePersistenceProvider().run(true, (RunCallback<Integer>) em -> getNext(em, key));
    }

    protected int getNext(EntityManager em, String key) {
        UIDSequenceBean seq;
        try {
            seq = em.createNamedQuery("UIDSequence.findByKey", UIDSequenceBean.class)
                    .setParameter("key", key)
                    .getSingleResult();
        } catch (NoResultException e) {
            seq = new UIDSequenceBean(key);
            em.persist(seq);
        }
        return seq.nextIndex();
    }

}
