/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool2.impl;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.pool2.BaseObjectPool;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.PoolableObjectFactory;

/**
 * A configurable {@link ObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link PoolableObjectFactory},
 * <tt>GenericObjectPool</tt> provides robust pooling functionality for
 * arbitrary objects.
 * <p>
 * A <tt>GenericObjectPool</tt> provides a number of configurable parameters:
 * <ul>
 * <li>
 *    {@link #setMaxActive <i>maxActive</i>} controls the maximum number of
 * objects that can be allocated by the pool (checked out to clients, or idle
 * awaiting checkout) at a given time. When non-positive, there is no limit to
 * the number of objects that can be managed by the pool at one time. When
 * {@link #setMaxActive <i>maxActive</i>} is reached, the pool is said to be
 * exhausted. The default setting for this parameter is 8.</li>
 * <li>
 *    {@link #setMaxIdle <i>maxIdle</i>} controls the maximum number of objects
 * that can sit idle in the pool at any time. When negative, there is no limit
 * to the number of objects that may be idle at one time. The default setting
 * for this parameter is 8.</li>
 * <li>
 *    {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} specifies the
 * behavior of the {@link #borrowObject} method when the pool is exhausted:
 * <ul>
 * <li>When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 * {@link WhenExhaustedAction#FAIL}, {@link #borrowObject} will throw a
 * {@link NoSuchElementException}</li>
 * <li>When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 * {@link WhenExhaustedAction#BLOCK}, {@link #borrowObject} will block (invoke
 * {@link Object#wait()}) until a new or idle object is available. If a positive
 * {@link #setMaxWait <i>maxWait</i>} value is supplied, then
 * {@link #borrowObject} will block for at most that many milliseconds, after
 * which a {@link NoSuchElementException} will be thrown. If {@link #setMaxWait
 * <i>maxWait</i>} is non-positive, the {@link #borrowObject} method will block
 * indefinitely.</li>
 * </ul>
 * The default <code>whenExhaustedAction</code> setting is
 * {@link WhenExhaustedAction#BLOCK} and the default <code>maxWait</code> setting is
 * -1. By default, therefore, <code>borrowObject</code> will block indefinitely
 * until an idle instance becomes available.</li>
 * <li>When {@link #setTestOnBorrow <i>testOnBorrow</i>} is set, the pool will
 * attempt to validate each object before it is returned from the
 * {@link #borrowObject} method. (Using the provided factory's
 * {@link PoolableObjectFactory#validateObject} method.) Objects that fail to
 * validate will be dropped from the pool, and a different object will be
 * borrowed. The default setting for this parameter is <code>false.</code></li>
 * <li>When {@link #setTestOnReturn <i>testOnReturn</i>} is set, the pool will
 * attempt to validate each object before it is returned to the pool in the
 * {@link #returnObject} method. (Using the provided factory's
 * {@link PoolableObjectFactory#validateObject} method.) Objects that fail to
 * validate will be dropped from the pool. The default setting for this
 * parameter is <code>false.</code></li>
 * </ul>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects are available. This is performed by an "idle object eviction" thread,
 * which runs asynchronously. Caution should be used when configuring this
 * optional feature. Eviction runs contend with client threads for access to
 * objects in the pool, so if they run too frequently performance issues may
 * result. The idle object eviction thread may be configured using the following
 * attributes:
 * <ul>
 * <li>
 *   {@link #setTimeBetweenEvictionRunsMillis
 * <i>timeBetweenEvictionRunsMillis</i>} indicates how long the eviction thread
 * should sleep before "runs" of examining idle objects. When non-positive, no
 * eviction thread will be launched. The default setting for this parameter is
 * -1 (i.e., idle object eviction is disabled by default).</li>
 * <li>
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 * specifies the minimum amount of time that an object may sit idle in the pool
 * before it is eligible for eviction due to idle time. When non-positive, no
 * object will be dropped from the pool due to idle time alone. This setting has
 * no effect unless <code>timeBetweenEvictionRunsMillis > 0.</code> The default
 * setting for this parameter is 30 minutes.</li>
 * <li>
 *   {@link #setTestWhileIdle <i>testWhileIdle</i>} indicates whether or not
 * idle objects should be validated using the factory's
 * {@link PoolableObjectFactory#validateObject} method. Objects that fail to
 * validate will be dropped from the pool. This setting has no effect unless
 * <code>timeBetweenEvictionRunsMillis > 0.</code> The default setting for this
 * parameter is <code>false.</code></li>
 * <li>
 *   {@link #setSoftMinEvictableIdleTimeMillis
 * <i>softMinEvictableIdleTimeMillis</i>} specifies the minimum amount of time
 * an object may sit idle in the pool before it is eligible for eviction by the
 * idle object evictor (if any), with the extra condition that at least
 * "minIdle" object instances remain in the pool. This setting has no
 * effect unless <code>timeBetweenEvictionRunsMillis > 0.</code> and it is
 * superseded by {@link #setMinEvictableIdleTimeMillis
 * <i>minEvictableIdleTimeMillis</i>} (that is, if
 * <code>minEvictableIdleTimeMillis</code> is positive, then
 * <code>softMinEvictableIdleTimeMillis</code> is ignored). The default setting
 * for this parameter is -1 (disabled).</li>
 * <li>
 *   {@link #setNumTestsPerEvictionRun <i>numTestsPerEvictionRun</i>}
 * determines the number of objects examined in each run of the idle object
 * evictor. This setting has no effect unless
 * <code>timeBetweenEvictionRunsMillis > 0.</code> The default setting for this
 * parameter is 3.</li>
 * </ul>
 * <p>
 * <p>
 * The pool can be configured to behave as a LIFO queue with respect to idle
 * objects - always returning the most recently used object from the pool, or as
 * a FIFO queue, where borrowObject always returns the oldest object in the idle
 * object pool.
 * <ul>
 * <li>
 *   {@link #setLifo <i>lifo</i>} determines whether or not the pool returns
 * idle objects in last-in-first-out order. The default setting for this
 * parameter is <code>true.</code></li>
 * </ul>
 * <p>
 * GenericObjectPool is not usable without a {@link PoolableObjectFactory}. A
 * non-<code>null</code> factory must be provided either as a constructor
 * argument or via a call to {@link #setFactory} before the pool is used.
 * <p>
 * Implementation note: To prevent possible deadlocks, care has been taken to
 * ensure that no call to a factory method will occur within a synchronization
 * block. See POOL-125 and DBCP-44 for more information.
 * 
 * @see GenericKeyedObjectPool
 * @param <T>
 *            Type of element pooled in this pool.
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @author Sandy McArthur
 * @version $Revision$ $Date: 2011-05-11 13:50:33 +0100 (Wed, 11 May
 *          2011) $
 * @since Pool 1.0
 */
public class GenericObjectPool<T> extends BaseObjectPool<T> {

    // --- constructors -----------------------------------------------

    /**
     * Create a new <tt>GenericObjectPool</tt> with default properties.
     */
    public GenericObjectPool() {
        this(new GenericObjectPoolConfig<T>());
    }

    public GenericObjectPool(GenericObjectPoolConfig<T> config) {
        this._factory = config.getFactory();
        this._lifo = config.getLifo();
        this.maxTotal = config.getMaxTotal();
        this._maxIdle = config.getMaxIdle();
        this._maxWait = config.getMaxWait();
        this._minEvictableIdleTimeMillis =
                config.getMinEvictableIdleTimeMillis();
        this._minIdle = config.getMinIdle();
        this._numTestsPerEvictionRun = config.getNumTestsPerEvictionRun();
        this._softMinEvictableIdleTimeMillis =
                config.getSoftMinEvictableIdleTimeMillis();
        this._testOnBorrow = config.getTestOnBorrow();
        this._testOnReturn = config.getTestOnReturn();
        this._testWhileIdle = config.getTestWhileIdle();
        this._timeBetweenEvictionRunsMillis =
                config.getTimeBetweenEvictionRunsMillis();
        this._whenExhaustedAction = config.getWhenExhaustedAction();

        startEvictor(_timeBetweenEvictionRunsMillis);
    }


    // --- public methods ---------------------------------------------

    // --- configuration methods --------------------------------------

    /**
     * Returns the maximum number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time. When
     * non-positive, there is no limit to the number of objects that can be
     * managed by the pool at one time.
     * 
     * @return the cap on the total number of object instances managed by the
     *         pool.
     * @see #setMaxTotal
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * Sets the cap on the number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time. Use
     * a negative value for no limit.
     * 
     * @param maxTotal
     *            The cap on the total number of object instances managed by the
     *            pool. Negative values mean that there is no limit to the
     *            number of objects allocated by the pool.
     * @see #getMaxTotal
     */
    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * Returns the action to take when the {@link #borrowObject} method is
     * invoked when the pool is exhausted (the maximum number of "active"
     * objects has been reached).
     * 
     * @return the action to take when the pool is exhuasted
     * @see #setWhenExhaustedAction
     */
    public WhenExhaustedAction getWhenExhaustedAction() {
        return _whenExhaustedAction;
    }

    /**
     * Sets the action to take when the {@link #borrowObject} method is invoked
     * when the pool is exhausted (the maximum number of "active" objects has
     * been reached).
     * 
     * @param whenExhaustedAction   action to take when the pool is exhausted
     * @see #getWhenExhaustedAction
     */
    public void setWhenExhaustedAction(
            WhenExhaustedAction whenExhaustedAction) {
        _whenExhaustedAction = whenExhaustedAction;
    }

    /**
     * Returns the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing an exception
     * when the pool is exhausted and the {@link #setWhenExhaustedAction
     * "when exhausted" action} is {@link WhenExhaustedAction#BLOCK}. When less than
     * or equal to 0, the {@link #borrowObject} method may block indefinitely.
     * 
     * @return maximum number of milliseconds to block when borrowing an object.
     * @see #setMaxWait
     * @see #setWhenExhaustedAction
     * @see WhenExhaustedAction#BLOCK
     */
    public long getMaxWait() {
        return _maxWait;
    }

    /**
     * Sets the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing an exception
     * when the pool is exhausted and the {@link #setWhenExhaustedAction
     * "when exhausted" action} is {@link WhenExhaustedAction#BLOCK}. When less than
     * or equal to 0, the {@link #borrowObject} method may block indefinitely.
     * 
     * @param maxWait
     *            maximum number of milliseconds to block when borrowing an
     *            object.
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     * @see WhenExhaustedAction#BLOCK
     */
    public void setMaxWait(long maxWait) {
        _maxWait = maxWait;
    }

    /**
     * Returns the cap on the number of "idle" instances in the pool.
     * 
     * @return the cap on the number of "idle" instances in the pool.
     * @see #setMaxIdle
     */
    public int getMaxIdle() {
        return _maxIdle;
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool. If maxIdle is
     * set too low on heavily loaded systems it is possible you will see objects
     * being destroyed and almost immediately new objects being created. This is
     * a result of the active threads momentarily returning objects faster than
     * they are requesting them them, causing the number of idle objects to rise
     * above maxIdle. The best value for maxIdle for heavily loaded system will
     * vary but the default is a good starting point.
     * 
     * @param maxIdle
     *            The cap on the number of "idle" instances in the pool. Use a
     *            negative value to indicate an unlimited number of idle
     *            instances.
     * @see #getMaxIdle
     */
    public void setMaxIdle(int maxIdle) {
        _maxIdle = maxIdle;
    }

    /**
     * Sets the minimum number of objects allowed in the pool before the evictor
     * thread (if active) spawns new objects. Note that no objects are created
     * when <code>numActive + numIdle >= maxActive.</code> This setting has no
     * effect if the idle object evictor is disabled (i.e. if
     * <code>timeBetweenEvictionRunsMillis <= 0</code>).
     * 
     * @param minIdle
     *            The minimum number of objects.
     * @see #getMinIdle
     * @see #getTimeBetweenEvictionRunsMillis()
     */
    public void setMinIdle(int minIdle) {
        _minIdle = minIdle;
    }

    /**
     * Returns the minimum number of objects allowed in the pool before the
     * evictor thread (if active) spawns new objects. (Note no objects are
     * created when: numActive + numIdle >= maxActive)
     * 
     * @return The minimum number of objects.
     * @see #setMinIdle
     */
    public int getMinIdle() {
        return _minIdle;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned by the {@link #borrowObject} method. If the object fails to
     * validate, it will be dropped from the pool, and we will attempt to borrow
     * another.
     * 
     * @return <code>true</code> if objects are validated before being borrowed.
     * @see #setTestOnBorrow
     */
    public boolean getTestOnBorrow() {
        return _testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned by the {@link #borrowObject} method. If the object fails to
     * validate, it will be dropped from the pool, and we will attempt to borrow
     * another.
     * 
     * @param testOnBorrow
     *            <code>true</code> if objects should be validated before being
     *            borrowed.
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        _testOnBorrow = testOnBorrow;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned to the pool within the {@link #returnObject}.
     * 
     * @return <code>true</code> when objects will be validated after returned
     *         to {@link #returnObject}.
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned to the pool within the {@link #returnObject}.
     * 
     * @param testOnReturn
     *            <code>true</code> so objects will be validated after returned
     *            to {@link #returnObject}.
     * @see #getTestOnReturn
     */
    public void setTestOnReturn(boolean testOnReturn) {
        _testOnReturn = testOnReturn;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the idle
     * object evictor thread. When non-positive, no idle object evictor thread
     * will be run.
     * 
     * @return number of milliseconds to sleep between evictor runs.
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public long getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the idle object
     * evictor thread. When non-positive, no idle object evictor thread will be
     * run.
     * 
     * @param timeBetweenEvictionRunsMillis
     *            number of milliseconds to sleep between evictor runs.
     * @see #getTimeBetweenEvictionRunsMillis
     */
    public void setTimeBetweenEvictionRunsMillis(
            long timeBetweenEvictionRunsMillis) {
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    /**
     * Returns the max number of objects to examine during each run of the idle
     * object evictor thread (if any).
     * 
     * @return max number of objects to examine during each evictor run.
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the max number of objects to examine during each run of the idle
     * object evictor thread (if any).
     * <p>
     * When a negative value is supplied,
     * <tt>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run. That is, when the value is <i>-n</i>, roughly one
     * <i>n</i>th of the idle objects will be tested per run. When the value is
     * positive, the number of tests actually performed in each run will be the
     * minimum of this value and the number of instances idle in the pool.
     * 
     * @param numTestsPerEvictionRun
     *            max number of objects to examine during each evictor run.
     * @see #getNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor (if any).
     * 
     * @return minimum amount of time an object may sit idle in the pool before
     *         it is eligible for eviction.
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public long getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction by the idle object evictor (if any). When
     * non-positive, no objects will be evicted from the pool due to idle time
     * alone.
     * 
     * @param minEvictableIdleTimeMillis
     *            minimum amount of time an object may sit idle in the pool
     *            before it is eligible for eviction.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * Returns the minimum amount of time
     * an object may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any), with the extra condition that at least
     * "minIdle" object instances remain in the pool. This setting has no
     * effect unless {@code timeBetweenEvictionRunsMillis > 0.} and it is
     * superseded by {@link #setMinEvictableIdleTimeMillis
     * <i>minEvictableIdleTimeMillis</i>} (that is, if
     * {@code minEvictableIdleTimeMillis} is positive, then
     * {@code softMinEvictableIdleTimeMillis} is ignored). The default setting
     * for this parameter is -1 (disabled).
     * 
     * @return minimum amount of time an object may sit idle in the pool before
     *         it is eligible for eviction if minIdle instances are available
     * @since Pool 1.3
     */
    public long getSoftMinEvictableIdleTimeMillis() {
        return _softMinEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction by the idle object evictor (if any), with the
     * extra condition that at least "minIdle" object instances remain in the
     * pool. When non-positive, no objects will be evicted from the pool due to
     * idle time alone.
     * 
     * @param softMinEvictableIdleTimeMillis
     *            minimum amount of time an object may sit idle in the pool
     *            before it is eligible for eviction.
     * @since Pool 1.3
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    public void setSoftMinEvictableIdleTimeMillis(
            long softMinEvictableIdleTimeMillis) {
        _softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} by the idle object
     * evictor (if any). If an object fails to validate, it will be dropped from
     * the pool.
     * 
     * @return <code>true</code> when objects will be validated by the evictor.
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} by the idle object
     * evictor (if any). If an object fails to validate, it will be dropped from
     * the pool.
     * 
     * @param testWhileIdle
     *            <code>true</code> so objects will be validated by the evictor.
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setTestWhileIdle(boolean testWhileIdle) {
        _testWhileIdle = testWhileIdle;
    }

    /**
     * Whether or not the idle object pool acts as a LIFO queue. True means that
     * borrowObject returns the most recently used ("last in") idle object in
     * the pool (if there are idle instances available). False means that the
     * pool behaves as a FIFO queue - objects are taken from the idle object
     * pool in the order that they are returned to the pool.
     * 
     * @return <code>true</true> if the pool is configured to act as a LIFO queue
     * @since 1.4
     */
    public boolean getLifo() {
        return _lifo;
    }

    /**
     * Sets the LIFO property of the pool. True means that borrowObject returns
     * the most recently used ("last in") idle object in the pool (if there are
     * idle instances available). False means that the pool behaves as a FIFO
     * queue - objects are taken from the idle object pool in the order that
     * they are returned to the pool.
     * 
     * @param lifo
     *            the new value for the LIFO property
     * @since 1.4
     */
    public void setLifo(boolean lifo) {
        this._lifo = lifo;
    }

    /**
     * Sets my configuration.
     * 
     * @param conf
     *            configuration to use.
     * @see GenericObjectPool.Config
     */
    public void setConfig(GenericObjectPoolConfig<T> conf) {
        setMaxIdle(conf.getMaxIdle());
        setMinIdle(conf.getMinIdle());
        setMaxTotal(conf.getMaxTotal());
        setMaxWait(conf.getMaxWait());
        setWhenExhaustedAction(conf.getWhenExhaustedAction());
        setTestOnBorrow(conf.getTestOnBorrow());
        setTestOnReturn(conf.getTestOnReturn());
        setTestWhileIdle(conf.getTestWhileIdle());
        setNumTestsPerEvictionRun(conf.getNumTestsPerEvictionRun());
        setMinEvictableIdleTimeMillis(conf.getMinEvictableIdleTimeMillis());
        setTimeBetweenEvictionRunsMillis(
                conf.getTimeBetweenEvictionRunsMillis());
        setSoftMinEvictableIdleTimeMillis(
                conf.getSoftMinEvictableIdleTimeMillis());
        setLifo(conf.getLifo());
    }

    // -- ObjectPool methods ------------------------------------------

    /**
     * <p>
     * Borrows an object from the pool.
     * </p>
     * <p>
     * If there is an idle instance available in the pool, then either the
     * most-recently returned (if {@link #getLifo() lifo} == true) or "oldest"
     * (lifo == false) instance sitting idle in the pool will be activated and
     * returned. If activation fails, or {@link #getTestOnBorrow() testOnBorrow}
     * is set to true and validation fails, the instance is destroyed and the
     * next available instance is examined. This continues until either a valid
     * instance is returned or there are no more idle instances available.
     * </p>
     * <p>
     * If there are no idle instances available in the pool, behavior depends on
     * the {@link #getMaxActive() maxActive} and (if applicable)
     * {@link #getWhenExhaustedAction() whenExhaustedAction} and
     * {@link #getMaxWait() maxWait} properties. If the number of instances
     * checked out from the pool is less than <code>maxActive,</code> a new
     * instance is created, activated and (if applicable) validated and returned
     * to the caller.
     * </p>
     * <p>
     * If the pool is exhausted (no available idle instances and no capacity to
     * create new ones), this method will either block (
     * {@link WhenExhaustedAction#BLOCK}) or throw a
     * <code>NoSuchElementException</code> ({@link WhenExhaustedAction#FAIL}). The
     * length of time that this method will block when
     * <code>whenExhaustedAction == WHEN_EXHAUSTED_BLOCK</code> is determined by
     * the {@link #getMaxWait() maxWait} property.
     * </p>
     * <p>
     * When the pool is exhausted, multiple calling threads may be
     * simultaneously blocked waiting for instances to become available. As of
     * pool 1.5, a "fairness" algorithm has been implemented to ensure that
     * threads receive available instances in request arrival order.
     * </p>
     * 
     * @return object instance
     * @throws NoSuchElementException
     *             if an instance cannot be returned
     */
    @Override
    public T borrowObject() throws Exception {

        assertOpen();

        PooledObject<T> p = null;

        // Get local copy of current config so it is consistent for entire
        // method execution
        WhenExhaustedAction whenExhaustedAction = _whenExhaustedAction;
        long maxWait = _maxWait;

        boolean create;

        while (p == null) {
            create = false;
            if (whenExhaustedAction == WhenExhaustedAction.FAIL) {
                p = _idleObjects.pollFirst();
                if (p == null) {
                    create = true;
                    p = create();
                }
                if (p == null) {
                    throw new NoSuchElementException("Pool exhausted");
                }
                if (!p.allocate()) {
                    p = null;
                }
            } else if (whenExhaustedAction == WhenExhaustedAction.BLOCK) {
                p = _idleObjects.pollFirst();
                if (p == null) {
                    create = true;
                    p = create();
                }
                if (p == null) {
                    if (maxWait < 1) {
                        p = _idleObjects.takeFirst();
                    } else {
                        p = _idleObjects.pollFirst(maxWait,
                                TimeUnit.MILLISECONDS);
                    }
                }
                if (p == null) {
                    throw new NoSuchElementException(
                            "Timeout waiting for idle object");
                }
                if (!p.allocate()) {
                    p = null;
                }
            }

            if (p != null) {
                try {
                    _factory.activateObject(p.getObject());
                } catch (Exception e) {
                    try {
                        destroy(p);
                    } catch (Exception e1) {
                        // Ignore - activation failure is more important
                    }
                    p = null;
                    if (create) {
                        NoSuchElementException nsee = new NoSuchElementException(
                                "Unable to activate object");
                        nsee.initCause(e);
                        throw nsee;
                    }
                }
                if (p != null && getTestOnBorrow()) {
                    boolean validate = false;
                    Throwable validationThrowable = null;
                    try {
                        validate = _factory.validateObject(p.getObject());
                    } catch (Throwable t) {
                        PoolUtils.checkRethrow(t);
                    }
                    if (!validate) {
                        try {
                            destroy(p);
                        } catch (Exception e) {
                            // Ignore - validation failure is more important
                        }
                        p = null;
                        if (create) {
                            NoSuchElementException nsee = new NoSuchElementException(
                                    "Unable to validate object");
                            nsee.initCause(validationThrowable);
                            throw nsee;
                        }
                    }
                }
            }
        }

        return p.getObject();
    }

    /**
     * <p>
     * Returns an object instance to the pool.
     * </p>
     * <p>
     * If {@link #getMaxIdle() maxIdle} is set to a positive value and the
     * number of idle instances has reached this value, the returning instance
     * is destroyed.
     * </p>
     * <p>
     * If {@link #getTestOnReturn() testOnReturn} == true, the returning
     * instance is validated before being returned to the idle instance pool. In
     * this case, if validation fails, the instance is destroyed.
     * </p>
     * 
     * @param obj
     *            instance to return to the pool
     */
    @Override
    public void returnObject(T obj) {

        PooledObject<T> p = _allObjects.get(obj);

        if (p == null) {
            throw new IllegalStateException(
                    "Returned object not currently part of this pool");
        }

        if (getTestOnReturn()) {
            if (!_factory.validateObject(obj)) {
                try {
                    destroy(p);
                } catch (Exception e) {
                    // TODO - Ignore?
                }
                return;
            }
        }

        try {
            _factory.passivateObject(obj);
        } catch (Exception e1) {
            try {
                destroy(p);
            } catch (Exception e) {
                // TODO - Ignore?
            }
            return;
        }

        if (!p.deallocate()) {
            throw new IllegalStateException(
                    "Object has already been retured to this pool");
        }

        int maxIdle = getMaxIdle();
        if (isClosed() || maxIdle > -1 && maxIdle <= _idleObjects.size()) {
            try {
                destroy(p);
            } catch (Exception e) {
                // TODO - Ignore?
            }
        } else {
            if (getLifo()) {
                _idleObjects.addFirst(p);
            } else {
                _idleObjects.addLast(p);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to
     * destroy the instance.
     * </p>
     * 
     * @throws Exception
     *             if the configured {@link PoolableObjectFactory} throws an
     *             exception destroying obj
     */
    @Override
    public void invalidateObject(T obj) throws Exception {
        PooledObject<T> p = _allObjects.get(obj);
        if (p == null) {
            throw new IllegalStateException(
                    "Object not currently part of this pool");
        }
        destroy(p);
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured
     * {@link PoolableObjectFactory#destroyObject(Object)} method on each idle
     * instance.
     * <p>
     * Implementation notes:
     * <ul>
     * <li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being returned to the
     * idle instance pool, even during its execution. It locks the pool only
     * during instance removal. Additional instances may be returned while
     * removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed.</li>
     * </ul>
     * </p>
     */
    @Override
    public void clear() {
        PooledObject<T> p = _idleObjects.poll();

        while (p != null) {
            try {
                destroy(p);
            } catch (Exception e) {
                // TODO - Ignore?
            }
            p = _idleObjects.poll();
        }
    }

    /**
     * Return the number of instances currently borrowed from this pool.
     * 
     * @return the number of instances currently borrowed from this pool
     */
    @Override
    public int getNumActive() {
        return _allObjects.size() - _idleObjects.size();
    }

    /**
     * Return the number of instances currently idle in this pool.
     * 
     * @return the number of instances currently idle in this pool
     */
    @Override
    public int getNumIdle() {
        return _idleObjects.size();
    }

    /**
     * <p>
     * Closes the pool. Once the pool is closed, {@link #borrowObject()} will
     * fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned
     * objects destroyed on return.
     * </p>
     * <p>
     * Destroys idle instances in the pool by invoking {@link #clear()}.
     * </p>
     * 
     * @throws Exception
     */
    @Override
    public void close() throws Exception {
        super.close();
        clear();
        startEvictor(-1L);
    }

    /**
      * <p>Sets the poolable object factory associated with this pool.</p>
      * 
      * <p>If this method is called when the factory has previously been set an
      * IllegalStateException is thrown.</p>
     * 
     * @param factory
     *            the {@link PoolableObjectFactory} used to create new
     *            instances.
      * @throws IllegalStateException if the factory has already been set
     */
    @Override
    public void setFactory(PoolableObjectFactory<T> factory)
            throws IllegalStateException {
        if (this._factory == null) {
            synchronized (factoryLock) {
                if (this._factory == null) {
                    this._factory = factory;
                } else {
                    throw new IllegalStateException("Factory already set");
                }
            }
        } else {
            throw new IllegalStateException("Factory already set");
        }
    }

    /**
     * <p>
     * Perform <code>numTests</code> idle object eviction tests, evicting
     * examined objects that meet the criteria for eviction. If
     * <code>testWhileIdle</code> is true, examined objects are validated when
     * visited (and removed if invalid); otherwise only objects that have been
     * idle for more than <code>minEvicableIdletimeMillis</code> are removed.
     * </p>
     * <p>
     * Successive activations of this method examine objects in in sequence,
     * cycling through objects in oldest-to-youngest order.
     * </p>
     * 
     * @throws Exception
     *             if the pool is closed or eviction fails.
     */
    public void evict() throws Exception {
        assertOpen();

        if (_idleObjects.size() == 0) {
            return;
        }

        PooledObject<T> underTest = null;

        boolean testWhileIdle = getTestWhileIdle();
        long idleEvictTime = Long.MAX_VALUE;
        long idleSoftEvictTime = Long.MAX_VALUE;
        
        if (getMinEvictableIdleTimeMillis() > 0) {
            idleEvictTime = getMinEvictableIdleTimeMillis();
        }
        if (getSoftMinEvictableIdleTimeMillis() > 0) {
            idleSoftEvictTime = getSoftMinEvictableIdleTimeMillis();
        }
                
        for (int i = 0, m = getNumTests(); i < m; i++) {
            if (_evictionIterator == null || !_evictionIterator.hasNext()) {
                if (getLifo()) {
                    _evictionIterator = _idleObjects.descendingIterator();
                } else {
                    _evictionIterator = _idleObjects.iterator();
                }
            }
            if (!_evictionIterator.hasNext()) {
                // Pool exhausted, nothing to do here
                return;
            }

            try {
                underTest = _evictionIterator.next();
            } catch (NoSuchElementException nsee) {
                // Object was borrowed in another thread
                // Don't count this as an eviction test so reduce i;
                i--;
                _evictionIterator = null;
                continue;
            }

            if (!underTest.startEvictionTest()) {
                // Object was borrowed in another thread
                // Don't count this as an eviction test so reduce i;
                i--;
                continue;
            }

            if (idleEvictTime < underTest.getIdleTimeMillis() ||
                    (idleSoftEvictTime < underTest.getIdleTimeMillis() &&
                            getMinIdle() < _idleObjects.size())) {
                destroy(underTest);
            } else {
                if (testWhileIdle) {
                    boolean active = false;
                    try {
                        _factory.activateObject(underTest.getObject());
                        active = true;
                    } catch (Exception e) {
                        destroy(underTest);
                    }
                    if (active) {
                        if (!_factory.validateObject(underTest.getObject())) {
                            destroy(underTest);
                        } else {
                            try {
                                _factory.passivateObject(underTest.getObject());
                            } catch (Exception e) {
                                destroy(underTest);
                            }
                        }
                    }
                }
                if (!underTest.endEvictionTest(_idleObjects)) {
                    // TODO - May need to add code here once additional states
                    // are used
                }
            }
        }

        return;
    }

    private PooledObject<T> create() throws Exception {
        int localMaxTotal = getMaxTotal();
        long newCreateCount = createCount.incrementAndGet();
        if (localMaxTotal > -1 && newCreateCount > localMaxTotal ||
                newCreateCount > Integer.MAX_VALUE) {
            createCount.decrementAndGet();
            return null;
        }

        T t = null;
        try {
            t = _factory.makeObject();
        } catch (Exception e) {
            createCount.decrementAndGet();
            throw e;
        }

        PooledObject<T> p = new PooledObject<T>(t);
        _allObjects.put(t, p);
        return p;
    }

    private void destroy(PooledObject<T> toDestory) throws Exception {
        _idleObjects.remove(toDestory);
        _allObjects.remove(toDestory.getObject());
        try {
            _factory.destroyObject(toDestory.getObject());
        } finally {
            createCount.decrementAndGet();
        }
    }

    /**
     * Check to see if we are below our minimum number of objects if so enough
     * to bring us back to our minimum.
     * 
     * @throws Exception
     *             when {@link #addObject()} fails.
     */
    private void ensureMinIdle() throws Exception {
        int minIdle = getMinIdle();
        if (minIdle < 1) {
            return;
        }

        while (_idleObjects.size() < minIdle) {
            PooledObject<T> p = create();
            if (p == null) {
                // Can't create objects, no reason to think another call to
                // create will work. Give up.
                break;
            }
            if (getLifo()) {
                _idleObjects.addFirst(p);
            } else {
                _idleObjects.addLast(p);
            }
        }
    }

    /**
     * Create an object, and place it into the pool. addObject() is useful for
     * "pre-loading" a pool with idle objects.
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException(
                    "Cannot add objects without a factory.");
        }
        PooledObject<T> p = create();
        addIdleObject(p);
    }

    // --- non-public methods ----------------------------------------

    private void addIdleObject(PooledObject<T> p) throws Exception {
        if (p != null) {
            _factory.passivateObject(p.getObject());
            if (getLifo()) {
                _idleObjects.addFirst(p);
            } else {
                _idleObjects.addLast(p);
            }
        }
    }

    /**
     * Start the eviction thread or service, or when <i>delay</i> is
     * non-positive, stop it if it is already running.
     * 
     * @param delay
     *            milliseconds between evictor runs.
     */
    protected void startEvictor(long delay) {
        if (null != _evictor) {
            EvictionTimer.cancel(_evictor);
            _evictor = null;
        }
        if (delay > 0) {
            _evictor = new Evictor();
            EvictionTimer.schedule(_evictor, delay, delay);
        }
    }

    /**
     * Returns pool info including {@link #getNumActive()},
     * {@link #getNumIdle()} and a list of objects idle in the pool with their
     * idle times.
     * 
     * @return string containing debug information
     */
    synchronized String debugInfo() {
        StringBuilder buf = new StringBuilder();
        buf.append("Active: ").append(getNumActive()).append("\n");
        buf.append("Idle: ").append(getNumIdle()).append("\n");
        buf.append("Idle Objects:\n");
        for (PooledObject<T> pair : _idleObjects) {
            buf.append("\t").append(pair.toString());
        }
        return buf.toString();
    }

    /**
     * Returns the number of tests to be performed in an Evictor run, based on
     * the current value of <code>numTestsPerEvictionRun</code> and the number
     * of idle instances in the pool.
     * 
     * @see #setNumTestsPerEvictionRun
     * @return the number of tests for the Evictor to run
     */
    private int getNumTests() {
        if (_numTestsPerEvictionRun >= 0) {
            return Math.min(_numTestsPerEvictionRun, _idleObjects.size());
        } else {
            return (int) (Math.ceil(_idleObjects.size() /
                    Math.abs((double) _numTestsPerEvictionRun)));
        }
    }

    // --- inner classes ----------------------------------------------

    /**
     * The idle object evictor {@link TimerTask}.
     * 
     * @see GenericObjectPool#setTimeBetweenEvictionRunsMillis
     */
    private class Evictor extends TimerTask {
        /**
         * Run pool maintenance. Evict objects qualifying for eviction and then
         * invoke {@link GenericObjectPool#ensureMinIdle()}.
         */
        @Override
        public void run() {
            try {
                evict();
            } catch (Exception e) {
                // ignored
            } catch (OutOfMemoryError oome) {
                // Log problem but give evictor thread a chance to continue in
                // case error is recoverable
                oome.printStackTrace(System.err);
            }
            try {
                ensureMinIdle();
            } catch (Exception e) {
                // ignored
            }
        }
    }

    // --- private attributes ---------------------------------------

    /**
     * The cap on the number of idle instances in the pool.
     * 
     * @see #setMaxIdle
     * @see #getMaxIdle
     */
    private volatile int _maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    /**
     * The cap on the minimum number of idle instances in the pool.
     * 
     * @see #setMinIdle
     * @see #getMinIdle
     */
    private volatile int _minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    /**
     * The cap on the total number of active instances from the pool.
     * 
     * @see #setMaxTotal
     * @see #getMaxTotal
     */
    private volatile int maxTotal =
        GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * The maximum amount of time (in millis) the {@link #borrowObject} method
     * should block before throwing an exception when the pool is exhausted and
     * the {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link WhenExhaustedAction#BLOCK}. When less than or equal to 0, the
     * {@link #borrowObject} method may block indefinitely.
     * 
     * @see #setMaxWait
     * @see #getMaxWait
     * @see WhenExhaustedAction#BLOCK
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private volatile long _maxWait = GenericObjectPoolConfig.DEFAULT_MAX_WAIT;

    /**
     * The action to take when the {@link #borrowObject} method is invoked when
     * the pool is exhausted (the maximum number of "active" objects has been
     * reached).
     * 
     * @see #DEFAULT_WHEN_EXHAUSTED_ACTION
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private volatile WhenExhaustedAction _whenExhaustedAction =
        GenericObjectPoolConfig.DEFAULT_WHEN_EXHAUSTED_ACTION;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned by the {@link #borrowObject} method. If the object fails to
     * validate, it will be dropped from the pool, and we will attempt to borrow
     * another.
     * 
     * @see #setTestOnBorrow
     * @see #getTestOnBorrow
     */
    private volatile boolean _testOnBorrow =
        GenericObjectPoolConfig.DEFAULT_TEST_ON_BORROW;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} before being
     * returned to the pool within the {@link #returnObject}.
     * 
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    private volatile boolean _testOnReturn =
        GenericObjectPoolConfig.DEFAULT_TEST_ON_RETURN;

    /**
     * When <tt>true</tt>, objects will be
     * {@link PoolableObjectFactory#validateObject validated} by the idle object
     * evictor (if any). If an object fails to validate, it will be dropped from
     * the pool.
     * 
     * @see #setTestWhileIdle
     * @see #getTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private volatile boolean _testWhileIdle =
        GenericObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;

    /**
     * The number of milliseconds to sleep between runs of the idle object
     * evictor thread. When non-positive, no idle object evictor thread will be
     * run.
     * 
     * @see #setTimeBetweenEvictionRunsMillis
     * @see #getTimeBetweenEvictionRunsMillis
     */
    private volatile long _timeBetweenEvictionRunsMillis =
        GenericObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The max number of objects to examine during each run of the idle object
     * evictor thread (if any).
     * <p>
     * When a negative value is supplied,
     * <tt>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</tt>
     * tests will be run. I.e., when the value is <i>-n</i>, roughly one
     * <i>n</i>th of the idle objects will be tested per run.
     * 
     * @see #setNumTestsPerEvictionRun
     * @see #getNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private volatile int _numTestsPerEvictionRun =
        GenericObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * The minimum amount of time an object may sit idle in the pool before it
     * is eligible for eviction by the idle object evictor (if any). When
     * non-positive, no objects will be evicted from the pool due to idle time
     * alone.
     * 
     * @see #setMinEvictableIdleTimeMillis
     * @see #getMinEvictableIdleTimeMillis
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private volatile long _minEvictableIdleTimeMillis =
        GenericObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * The minimum amount of time an object may sit idle in the pool before it
     * is eligible for eviction by the idle object evictor (if any), with the
     * extra condition that at least "minIdle" amount of object remain in the
     * pool. When non-positive, no objects will be evicted from the pool due to
     * idle time alone.
     * 
     * @see #setSoftMinEvictableIdleTimeMillis
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    private volatile long _softMinEvictableIdleTimeMillis =
        GenericObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /** Whether or not the pool behaves as a LIFO queue (last in first out) */
    private volatile boolean _lifo = GenericObjectPoolConfig.DEFAULT_LIFO;

    /** My {@link PoolableObjectFactory}. */
    private volatile PoolableObjectFactory<T> _factory;
    final private Object factoryLock = new Object();

    /**
     * My idle object eviction {@link TimerTask}, if any.
     */
    private Evictor _evictor = null;

    /**
     * All of the objects currently associated with this pool in any state. It
     * excludes objects that have been destroyed. The size of
     * {@link #_allObjects} will always be less than or equal to {@link
     * #_maxActive}.
     */
    private final Map<T, PooledObject<T>> _allObjects =
        new ConcurrentHashMap<T, PooledObject<T>>();

    /**
     * The combined count of the currently created objects and those in the
     * process of being created. Under load, it may exceed {@link #_maxActive}
     * if multiple threads try and create a new object at the same time but
     * {@link #create(boolean)} will ensure that there are never more than
     * {@link #_maxActive} objects created at any one time.
     */
    private final AtomicLong createCount = new AtomicLong(0);

    /** The queue of idle objects */
    private final LinkedBlockingDeque<PooledObject<T>> _idleObjects =
        new LinkedBlockingDeque<PooledObject<T>>();

    /** An iterator for {@link #_idleObjects} that is used by the evictor. */
    private Iterator<PooledObject<T>> _evictionIterator = null;
}
