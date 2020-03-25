/*
	Milyn - Copyright (C) 2006 - 2010

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License (version 2.1) as published by the Free Software
	Foundation.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

	See the GNU Lesser General Public License for more details:
	http://www.gnu.org/licenses/lgpl.txt
*/
package org.smooks.persistence;

import org.smooks.SmooksException;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.cdr.annotation.AppContext;
import org.smooks.cdr.annotation.ConfigParam;
import org.smooks.cdr.annotation.ConfigParam.Use;
import org.smooks.container.ApplicationContext;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.Fragment;
import org.smooks.delivery.annotation.Initialize;
import org.smooks.delivery.annotation.VisitAfterIf;
import org.smooks.delivery.annotation.VisitBeforeIf;
import org.smooks.delivery.dom.DOMElementVisitor;
import org.smooks.delivery.ordering.Consumer;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.SAXElement;
import org.smooks.delivery.sax.SAXVisitAfter;
import org.smooks.delivery.sax.SAXVisitBefore;
import org.smooks.event.report.annotation.VisitAfterReport;
import org.smooks.event.report.annotation.VisitBeforeReport;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.context.BeanIdStore;
import org.smooks.javabean.repository.BeanId;
import org.smooks.persistence.util.PersistenceUtil;
import org.smooks.scribe.invoker.DaoInvoker;
import org.smooks.scribe.invoker.DaoInvokerFactory;
import org.smooks.scribe.register.DaoRegister;
import org.smooks.util.CollectionsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;


/**
 * DAO Deleter
 * <p />
 * This DAO deleter calls the delete method of a DAO, using a entity bean from
 * the bean context as parameter.
 *
 *
 *
 * <h3>Configuration</h3>
 * <b>Namespace:</b> https://www.smooks.org/xsd/smooks/persistence-1.5.xsd<br>
 * <b>Element:</b> deleter<br>
 * <b>Attributes:</b>
 * <ul>
 *  <li><b>beanId</b> : The id under which the entity bean is bound in the bean context. (<i>required</i>)
 *  <li><b>deleteOnElement</b> : The element selector to select the element when the delete should execute. (<i>required</i>)
 * 	<li><b>dao</b> : The name of the DAO that will be used. If it is not set then the default DAO is used. (<i>optional</i>)
 *  <li><b>name*</b> : The name of the insert method. Depending of the adapter this can mean different things.
 *                     For instance when using annotated DAO's you can name the methods and target them with this property, but
 *                     when using the Ibatis adapter you set the id of the Ibatis statement in this attribute. (<i>optional</i>)
 *  <li><b>deletedBeanId</b> : The bean id under which the deleted bean will be stored. If not set then the deleted
 *                             bean will not be stored in bean context. (<i>optional</i>)
 *  <li><b>deleteBefore</b> : If the deleter should execute on the 'before' event. (<i>default: false</i>)
 * </ul>
 *
 * <i>* This attribute is not supported by all scribe adapters.</i>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * &lt;?xml version=&quot;1.0&quot;?&gt;
 * &lt;smooks-resource-list xmlns=&quot;https://www.smooks.org/xsd/smooks-1.2.xsd&quot;
 *   xmlns:dao=&quot;https://www.smooks.org/xsd/smooks/persistence-1.5.xsd&quot;&gt;
 *
 *      &lt;dao:deleter dao=&quot;dao&quot; name=&quot;deleteIt&quot; beanId=&quot;toDelete1&quot; deleteOnElement=&quot;root&quot; deletedBeanId=&quot;deleted&quot; deleteBefore=&quot;false&quot; /&gt;
 *
 * &lt;/smooks-resource-list&gt;
 * </pre>
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 */
@VisitBeforeIf(	condition = "parameters.containsKey('deleteBefore') && parameters.deleteBefore.value == 'true'")
@VisitAfterIf( condition = "!parameters.containsKey('deleteBefore') || parameters.deleteBefore.value != 'true'")
@VisitBeforeReport(summary = "Deleting bean under beanId '${resource.parameters.beanId}'.", detailTemplate="reporting/EntityDeleter.html")
@VisitAfterReport(summary = "Deleting bean under beanId '${resource.parameters.beanId}'.", detailTemplate="reporting/EntityDeleter.html")
public class EntityDeleter implements DOMElementVisitor, SAXVisitBefore, SAXVisitAfter, Consumer, Producer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityDeleter.class);

    @ConfigParam(name = "beanId")
    private String beanIdName;

    @ConfigParam(name = "deletedBeanId", use = Use.OPTIONAL)
    private String deletedBeanIdName;

    @ConfigParam(name = "dao", use = Use.OPTIONAL)
    private String daoName;

    @ConfigParam(use = Use.OPTIONAL)
    private String name;

    @AppContext
    private ApplicationContext appContext;

    private ApplicationContextObjectStore objectStore;

    private BeanId beanId;

    private BeanId deletedBeanId;

    @Initialize
    public void initialize() throws SmooksConfigurationException {

    	BeanIdStore beanIdStore = appContext.getBeanIdStore();

    	beanId = beanIdStore.register(beanIdName);

    	if(deletedBeanIdName != null) {
    		deletedBeanId = beanIdStore.register(deletedBeanIdName);
    	}

    	objectStore = new ApplicationContextObjectStore(appContext);
    }

	/* (non-Javadoc)
	 * @see org.smooks.delivery.ordering.Producer#getProducts()
	 */
	public Set<String> getProducts() {
		if(deletedBeanIdName == null) {
			return Collections.emptySet();
		} else {
			return CollectionsUtil.toSet(deletedBeanIdName);
		}
	}


	/* (non-Javadoc)
	 * @see org.smooks.delivery.ordering.Consumer#consumes(java.lang.String)
	 */
	public boolean consumes(Object object) {
		return object.equals(beanIdName);
	}

    public void visitBefore(final Element element, final ExecutionContext executionContext) throws SmooksException {
    	delete(executionContext, new Fragment(element));
    }

    public void visitAfter(final Element element, final ExecutionContext executionContext) throws SmooksException {
    	delete(executionContext, new Fragment(element));
    }

    public void visitBefore(final SAXElement element, final ExecutionContext executionContext) throws SmooksException, IOException {
    	delete(executionContext, new Fragment(element));
    }

    public void visitAfter(final SAXElement element, final ExecutionContext executionContext) throws SmooksException, IOException {
    	delete(executionContext, new Fragment(element));
    }

	/**
	 * @param executionContext
	 * @param source
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private void delete(final ExecutionContext executionContext, final Fragment source) {

		if(LOGGER.isDebugEnabled()) {
			LOGGER.debug("Deleting bean under BeanId '" + beanIdName + "' with DAO '" + daoName + "'");
		}

		BeanContext beanContext = executionContext.getBeanContext();

		Object bean = beanContext.getBean(beanId);

		final DaoRegister emr = PersistenceUtil.getDAORegister(executionContext);

		Object dao = null;
		try {
			if(daoName == null) {
				dao = emr.getDefaultDao();
			} else {
				dao = emr.getDao(daoName);
			}

			if(dao == null) {
				String msg = "The DAO register returned null while getting the ";

				if(daoName == null) {
					msg += "default DAO";
				} else {
					msg += "DAO '" + daoName + "'";
				}

				throw new NullPointerException(msg);
			}

			DaoInvoker daoInvoker = DaoInvokerFactory.getInstance().create(dao, objectStore);

			Object result = (name != null) ? daoInvoker.delete(name, bean) : daoInvoker.delete(bean);

			if(deletedBeanId != null) {
				if(result == null) {
					result = bean;
				}
				beanContext.addBean(deletedBeanId, result, source);
			} else if(result != null && bean != result) {
				beanContext.changeBean(beanId, bean, source);
			}


		} finally {
			if(dao != null) {
				emr.returnDao(dao);
			}
		}
	}





}