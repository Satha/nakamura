package org.sakaiproject.kernel.message.listener;

/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.kernel.api.message.MessageConstants;
import org.sakaiproject.kernel.api.message.MessageRouterManager;
import org.sakaiproject.kernel.api.message.MessageRoutes;
import org.sakaiproject.kernel.api.message.MessageTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * 
 * @scr.component inherit="true" label="%sakai-event.name" immediate="true"
 * @scr.service interface="org.osgi.service.event.EventHandler"
 * @scr.property name="service.description"
 *               value="Event Handler Listening to Pending Messages Events"
 * @scr.property name="service.vendor" value="The Sakai Foundation"
 * @scr.property name="event.topics" value="org/sakaiproject/kernel/message/pending"
 * @scr.reference name="MessageTransport"
 *                interface="org.sakaiproject.kernel.api.message.MessageTransport"
 *                policy="dynamic" cardinality="0..n" bind="addTransport"
 *                unbind="removeTransport"
 * @scr.reference name="MessageRouterManager" interface="org.sakaiproject.kernel.api.message.MessageRouterManager"
 * @scr.reference name="SlingRepository" interface="org.apache.sling.jcr.api.SlingRepository"
 */
public class MessageSentListener implements EventHandler {
  private static final Logger LOG = LoggerFactory.getLogger(MessageSentListener.class);

  /**
   * This will contain all the transports.
   */
  private Map<MessageTransport, MessageTransport> transports = new ConcurrentHashMap<MessageTransport, MessageTransport>();

  private MessageRouterManager messageRouterManager;
  private SlingRepository slingRepository;
  protected void bindMessageRouterManager(MessageRouterManager messageRouterManager) {
    this.messageRouterManager = messageRouterManager;
  }
  protected void unbindMessageRouterManager(MessageRouterManager messageRouterManager) {
    this.messageRouterManager = null;
  }

  protected void bindSlingRepository(SlingRepository slingRepository) {
    this.slingRepository = slingRepository;
  }
  protected void unbindSlingRepository(SlingRepository slingRepository) {
    this.slingRepository = null;
  }

  private Session session;
  /**
   * Allows us to bind a session (for junit)
   */
  protected void bindSession(Session session) {
    this.session = session;
  }

  protected void activate(ComponentContext context) {
    try {
      session = slingRepository.loginAdministrative(null);
      LOG.info("Logged into the repository as an admin.");
    } catch (RepositoryException e) {
      LOG.warn("Unable to login to get a session for the message listener. {}",
          e.getMessage());
      e.printStackTrace();
    }
  }

  protected void deactivate(ComponentContext context) {
    if (session != null) {
      session.logout();
    }
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
   */
  public void handleEvent(Event event) {

    // Get the message
    // get the node, call up the appropriate handler and pass off based on
    // message type
    LOG.debug("handleEvent called");
    try {
      String path = (String) event.getProperty(MessageConstants.EVENT_LOCATION);
      Node n = (Node) session.getItem(path);
      String resourceType = n.getProperty(
          JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).getString();
      if (resourceType.equals(MessageConstants.SAKAI_MESSAGE_RT)) {

        MessageRoutes routes = messageRouterManager.getMessageRouting(n);
        
        for (MessageTransport transport : transports.values()) {
          transport.send(routes, event, n);
        }
      }
    } catch (LoginException e) {
      LOG.error(e.getMessage(), e);
    } catch (RepositoryException e) {
      LOG.error(e.getMessage(), e);
    }
  }

  /**
   * @param handler
   */
  protected void removeTransport(MessageTransport transport) {
    transports.remove(transport);
  }

  /**
   * @param handler
   */
  protected void addTransport(MessageTransport transport) {
    transports.put(transport,transport);
  }


}
