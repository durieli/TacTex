/*
 * TacTex - a power trading agent that competed in the Power Trading Agent Competition (Power TAC) www.powertac.org
 * Copyright (c) 2013-2016 Daniel Urieli and Peter Stone {urieli,pstone}@cs.utexas.edu               
 *
 *
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:  
 *
 *     Copyright 2011 the original author or authors.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an
 *     "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *     either express or implied. See the License for the specific language
 *     governing permissions and limitations under the License.
 */

package edu.utexas.cs.tactex.servercustomers.factoredcustomer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.w3c.dom.*;

import edu.utexas.cs.tactex.servercustomers.common.interfaces.InitializationService;
import edu.utexas.cs.tactex.servercustomers.common.interfaces.NewTariffListener;
import edu.utexas.cs.tactex.servercustomers.common.interfaces.ServerConfiguration;
import edu.utexas.cs.tactex.servercustomers.common.interfaces.TariffMarket;
import edu.utexas.cs.tactex.servercustomers.common.interfaces.TimeslotPhaseProcessor;
import edu.utexas.cs.tactex.servercustomers.common.repo.RandomSeedRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherForecastRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.ServerBasedWeatherReportRepo;
import edu.utexas.cs.tactex.servercustomers.common.repo.TariffSubscriptionRepo;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.CustomerFactory.CustomerCreator;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.FactoredCustomer;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.utils.SeedIdGenerator;
import edu.utexas.cs.tactex.utils.BrokerUtils;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TimeService;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Key class that processes the configuration file and creates a collection of customers 
 * during the initialization process.  It also delegates tariff selection callbacks and 
 * timeslot activation (i.e., capacity reporting) to the collection of customers.
 * 
 * @author Prashant Reddy
 */
//@Service  // allow autowiring
public class FactoredCustomerService //extends TimeslotPhaseProcessor
    //implements InitializationService, NewTariffListener
{
    private static Logger log = Logger.getLogger(FactoredCustomerService.class.getName());
//
//    @Autowired
    private TimeService timeService;
//    
//    @Autowired
    private TariffMarket tariffMarketService;
//    
//    @Autowired
    private TariffRepo tariffRepo;
//  
//    @Autowired
//    private ServerConfiguration serverConfig;
//    
//    @Autowired
    private CustomerRepo customerRepo = new CustomerRepo();
//    
//    @Autowired
//    private TariffRepo getTariffRepo;
//    
//    @Autowired
    private TimeslotRepo timeslotRepo;
//    
//    @Autowired
    private RandomSeedRepo randomSeedRepo = new RandomSeedRepo();
//    
//    @Autowired
    private TariffSubscriptionRepo tariffSubscriptionRepo = new TariffSubscriptionRepo();
//    
//    @Autowired
    private ServerBasedWeatherReportRepo weatherReportRepo;
//    
//    @Autowired
    private ServerBasedWeatherForecastRepo weatherForecastRepo;
//
//    @ConfigurableValue(valueType = "String", description = "Resource name for configuration data")
    private String configResource = "FactoredCustomers.xml";//null;
//
//    @ConfigurableValue(valueType = "Boolean", description = "Toggle logging of tariff allocation details")
//    private boolean allocationDetailsLogging = true;
//    @ConfigurableValue(valueType = "Boolean", description = "Toogle logging of capacity adjustment details")
    private boolean capacityDetailsLogging = false;
//    @ConfigurableValue(valueType = "Boolean", description = "Toggle logging of expected usage charges")
    private boolean usageChargesLogging = true;
//
    private List<CustomerStructure> customerStructures = new ArrayList<CustomerStructure>();
    private List<FactoredCustomer> customers = new ArrayList<FactoredCustomer>();
    private CustomerFactory customerFactory = new CustomerFactory();
//
//
//    public FactoredCustomerService()
//    {
//        super();
//    }
//
//    @Override
//    public void setDefaults ()
//    {
//    }
//
//    /**
//     * This is called once at the beginning of each game.
//     */
//    @Override
//    public String initialize (Competition competition, List<String> completedInits)
    //public String initialize (CustomerRepo customerRepo, TimeslotRepo timeslotRepo)
    public String initialize (TimeslotRepo timeslotRepo, TimeService timeService, ServerBasedWeatherReportRepo weatherReportRepo, ServerBasedWeatherForecastRepo weatherForecastRepo)
    {
//        if (! completedInits.contains("DefaultBroker") || ! completedInits.contains("TariffMarket")) {
//            log.debug("Waiting for DefaultBroker and TariffMarket to initialize");
//            return null;
//        }

        customerStructures.clear();
        customers.clear();
        SeedIdGenerator.reset();
        
        
        // sending our (common) repo to here
//        this.customerRepo = customerRepo;
        this.timeslotRepo = timeslotRepo;
        this.timeService = timeService;
        this.weatherReportRepo = weatherReportRepo;
        this.weatherForecastRepo = weatherForecastRepo;
        

//        super.init();
//        serverConfig.configureMe(this);

//        tariffMarketService.registerNewTariffListener(this);
    
        registerAvailableCustomerCreators();
//    
        loadCustomerStructures(configResource);
    
        log.info("Creating factored customers from configuration structures...");
        for (CustomerStructure customerStructure: customerStructures) { 
            FactoredCustomer customer = customerFactory.processStructure(customerStructure);
            if (customer != null) {
                customer.initialize(this, customerStructure);
                customers.add(customer);
            } else throw new Error("Could not create factored customer for structure: " + customerStructure.name);
        }
        log.info("Successfully initialized " + customers.size() + " factored customers from " + customerStructures.size() + " structures");     
        return "FactoredCustomer";
  }
    
  // mockable component access methods - package visibility
  TimeService getTimeService ()
  {
    return timeService;
  }
//
  public CustomerRepo getCustomerRepo ()
  {
    return customerRepo;
  }
  
  TariffRepo getTariffRepo ()
  {
    return tariffRepo;
  }
  
  TimeslotRepo getTimeslotRepo ()
  {
    return timeslotRepo;
  }
  
  RandomSeedRepo getRandomSeedRepo ()
  {
    return randomSeedRepo;
  }
  
  public TariffSubscriptionRepo getTariffSubscriptionRepo ()
  {
    return tariffSubscriptionRepo;
  }
  
  TariffMarket getTariffMarket ()
  {
    return tariffMarketService;
  }
  
  ServerBasedWeatherReportRepo getWeatherReportRepo ()
  {
    return weatherReportRepo;
  }
  
  ServerBasedWeatherForecastRepo getWeatherForecastRepo ()
  {
    return weatherForecastRepo;
  }

  private void registerAvailableCustomerCreators()
  {
      customerFactory.registerDefaultCreator(DefaultFactoredCustomer.getCreator());
      log.info("Registered default factored customer creator");
      
      List<String> creatorNames = new ArrayList<String>();
      creatorNames.add("edu.utexas.cs.tactex.servercustomers.factoredcustomer.LearningCustomerCreator");
      
      for (String name: creatorNames) {
          try {
              CustomerCreator creator = (CustomerCreator) Class.forName(name).newInstance();
              customerFactory.registerCreator(creator);
              log.info("Registered creator: " + name);
          } catch (ClassNotFoundException e) {
              continue;
          } catch (Exception e) {
              throw new Error("Could not register creator for name: " + name + "; caught exception: " + e);
          }
      }
  }

  protected void loadCustomerStructures(String configResource)
  {
      log.info("Attempting to load factored customer structures from config resource: " + configResource);
      try {
          InputStream configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(configResource);
          
          DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
          DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
          Document doc = docBuilder.parse(configStream);

          NodeList customerNodes = doc.getElementsByTagName("customer");
          int numStructures = customerNodes.getLength();
          log.info("Loading " + numStructures + " factored customer structures");
        
          for (int i = 0; i < numStructures; ++i) {
              Element customerElement = (Element) customerNodes.item(i);
              String name = customerElement.getAttribute("name");
              String countString = customerElement.getAttribute("count");
              int count;
              if (countString == null || countString.trim().isEmpty()) {
                  count = 1;
              } else {
                  count = Integer.parseInt(countString);            
              }
              if (count == 0) {
                  // ignore structure
              } else if (count == 1) {
                  CustomerStructure structure = new CustomerStructure(name, customerElement);
                  customerStructures.add(structure);                    
              } else {
                  for (int j=1; j <= count; ++j) {
                      CustomerStructure structure = new CustomerStructure(name + j, customerElement);
                      customerStructures.add(structure);                    
                  }
              }
          }
      } catch (Exception e) {
          log.error("Error loading factored customer structures from config resourcee: " + configResource + 
                    "; exception = " + e.toString());
          throw new Error(e);
      }
      log.info("Successfully loaded factored customer structures");
  }

//  @Override
//  public void publishNewTariffs(List<Tariff> tariffs)
//  {
//    // Find the subset of tariffs to evaluate
//    for (FactoredCustomer customer : customers) {
//      customer.evaluateTariffs();
//    }
//  }
//
//  @Override
//  public void updatedSubscriptionRepo() {
//    // Find the subset of tariffs to evaluate
//    for (FactoredCustomer customer : customers) {
//      customer.updatedSubscriptionRepo();
//    }
//  }
//
//  @Override
//  public void activate(Instant now, int phase)
//  {
//      for (FactoredCustomer customer : customers) {
//          customer.handleNewTimeslot();
//      }
//  }
//
//  String getConfigResource() 
//  {
//      return configResource;
//  }
//
//  void setConfigResource(String resource) 
//  {
//      configResource = resource;
//  }
//
//  boolean getAllocationDetailsLogging()
//  {
//      return allocationDetailsLogging;
//  }
//  
//  void setAllocationDetailsLogging(boolean value)
//  {
//      allocationDetailsLogging = value;
//  }
//  
  boolean getCapacityDetailsLogging()
  {
      return capacityDetailsLogging;
  }
//  
//  void setCapacityDetailsLogging(boolean value)
//  {
//      capacityDetailsLogging = value;
//  }
//  
  boolean getUsageChargesLogging()
  {
      return usageChargesLogging;
  }
  
//  void setUsageChargesLogging(boolean value)
//  {
//      usageChargesLogging = value;
//  }
//  
//  /** package scope for testing **/
//  List<FactoredCustomer> getCustomers() 
//  {
//      return customers;
//  }    
  
  // added, data access methods
  public List<FactoredCustomer> getCustomers() 
  {
      return customers;
  }

  // main functions for computing shifts
  public void predictAndOverwriteWithShiftingAndElasticity(int currentTimeslot) {
    for (FactoredCustomer customer : customers) {
      customer.updateWithShiftingAndElasticity(currentTimeslot);
    }
  }

  public void cleanSubscriptionRelatedData() {
    
    tariffSubscriptionRepo.recycle();
    
    for (FactoredCustomer customer : customers) {
      customer.cleanSubscriptionRelatedData();
    }
  }

  
}
