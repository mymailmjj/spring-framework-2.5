/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.aop.framework.autoproxy;

import java.io.IOException;

import junit.framework.TestCase;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.CountingBeforeAdvice;
import org.springframework.aop.framework.Lockable;
import org.springframework.aop.framework.LockedException;
import org.springframework.aop.framework.TimeStamped;
import org.springframework.aop.interceptor.NopInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.ITestBean;
import org.springframework.beans.TestBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Rod Johnson
 * @author Rob Harrop
 */
public class BeanNameAutoProxyCreatorTests extends TestCase {

	private BeanFactory beanFactory;

	protected void setUp() throws IOException {
		// Note that we need an ApplicationContext, not just a BeanFactory,
		// for post-processing and hence auto-proxying to work.
		this.beanFactory = new ClassPathXmlApplicationContext("beanNameAutoProxyCreatorTests.xml", getClass());
	}

	public void testNoProxy() {
		TestBean tb = (TestBean) beanFactory.getBean("noproxy");
		assertFalse(AopUtils.isAopProxy(tb));
		assertEquals("noproxy", tb.getName());
	}

	public void testJdkProxyWithExactNameMatch() {
		ITestBean tb = (ITestBean) beanFactory.getBean("onlyJdk");
		jdkAssertions(tb, 1);
		assertEquals("onlyJdk", tb.getName());
	}

	public void testJdkProxyWithDoubleProxying() {
		ITestBean tb = (ITestBean) beanFactory.getBean("doubleJdk");
		jdkAssertions(tb, 2);
		assertEquals("doubleJdk", tb.getName());
	}

	public void testJdkIntroduction() {
		ITestBean tb = (ITestBean) beanFactory.getBean("introductionUsingJdk");
		NopInterceptor nop = (NopInterceptor) beanFactory.getBean("introductionNopInterceptor");
		assertEquals(0, nop.getCount());
		assertTrue(AopUtils.isJdkDynamicProxy(tb));
		int age = 5;
		tb.setAge(age);
		assertEquals(age, tb.getAge());
		assertTrue("Introduction was made", tb instanceof TimeStamped);
		assertEquals(0, ((TimeStamped) tb).getTimeStamp());
		assertEquals(3, nop.getCount());		
		assertEquals("introductionUsingJdk", tb.getName());

		ITestBean tb2 = (ITestBean) beanFactory.getBean("second-introductionUsingJdk");

		// Check two per-instance mixins were distinct
		Lockable lockable1 = (Lockable) tb;
		Lockable lockable2 = (Lockable) tb2;
		assertFalse(lockable1.locked());
		assertFalse(lockable2.locked());
		tb.setAge(65);
		assertEquals(65, tb.getAge());
		lockable1.lock();
		assertTrue(lockable1.locked());
		// Shouldn't affect second
		assertFalse(lockable2.locked());
		// Can still mod second object
		tb2.setAge(12);
		// But can't mod first
		try {
			tb.setAge(6);
			fail("Mixin should have locked this object");
		}
		catch (LockedException ex) {
			// Ok
		}
	}

	public void testJdkIntroductionAppliesToCreatedObjectsNotFactoryBean() {
		ITestBean tb = (ITestBean) beanFactory.getBean("factory-introductionUsingJdk");
		NopInterceptor nop = (NopInterceptor) beanFactory.getBean("introductionNopInterceptor");
		assertEquals("NOP should not have done any work yet", 0, nop.getCount());
		assertTrue(AopUtils.isJdkDynamicProxy(tb));
		int age = 5;
		tb.setAge(age);
		assertEquals(age, tb.getAge());
		assertTrue("Introduction was made", tb instanceof TimeStamped);
		assertEquals(0, ((TimeStamped) tb).getTimeStamp());
		assertEquals(3, nop.getCount());		
	
		ITestBean tb2 = (ITestBean) beanFactory.getBean("second-introductionUsingJdk");
			
		// Check two per-instance mixins were distinct
		Lockable lockable1 = (Lockable) tb;
		Lockable lockable2 = (Lockable) tb2;
		assertFalse(lockable1.locked());
		assertFalse(lockable2.locked());
		tb.setAge(65);
		assertEquals(65, tb.getAge());
		lockable1.lock();
		assertTrue(lockable1.locked());
		// Shouldn't affect second
		assertFalse(lockable2.locked());
		// Can still mod second object
		tb2.setAge(12);
		// But can't mod first
		try {
			tb.setAge(6);
			fail("Mixin should have locked this object");
		}
		catch (LockedException ex) {
			// Ok
		}
	}

	public void testJdkProxyWithWildcardMatch() {
		ITestBean tb = (ITestBean) beanFactory.getBean("jdk1");
		jdkAssertions(tb, 1);
		assertEquals("jdk1", tb.getName());
	}

	public void testCglibProxyWithWildcardMatch() {
		TestBean tb = (TestBean) beanFactory.getBean("cglib1");
		cglibAssertions(tb);
		assertEquals("cglib1", tb.getName());
	}

	public void testWithFrozenProxy() {
		ITestBean testBean = (ITestBean) beanFactory.getBean("frozenBean");
		assertTrue(((Advised)testBean).isFrozen());
	}

	private void jdkAssertions(ITestBean tb, int nopInterceptorCount)  {
		NopInterceptor nop = (NopInterceptor) beanFactory.getBean("nopInterceptor");
		assertEquals(0, nop.getCount());
		assertTrue(AopUtils.isJdkDynamicProxy(tb));
		int age = 5;
		tb.setAge(age);
		assertEquals(age, tb.getAge());
		assertEquals(2 * nopInterceptorCount, nop.getCount());
	}

	/**
	 * Also has counting before advice.
	 */
	private void cglibAssertions(TestBean tb) {
		CountingBeforeAdvice cba = (CountingBeforeAdvice) beanFactory.getBean("countingBeforeAdvice");
		NopInterceptor nop = (NopInterceptor) beanFactory.getBean("nopInterceptor");
		assertEquals(0, cba.getCalls());
		assertEquals(0, nop.getCount());
		assertTrue(AopUtils.isCglibProxy(tb));
		int age = 5;
		tb.setAge(age);
		assertEquals(age, tb.getAge());
		assertEquals(2, nop.getCount());
		assertEquals(2, cba.getCalls());		
	}

}
