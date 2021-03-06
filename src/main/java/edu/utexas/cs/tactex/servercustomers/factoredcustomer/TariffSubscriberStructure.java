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

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.*;

import edu.utexas.cs.tactex.servercustomers.factoredcustomer.ProbabilityDistribution;
import edu.utexas.cs.tactex.servercustomers.factoredcustomer.interfaces.CapacityBundle;

/**
 * Data-holder class for parsed configuration elements of one tariff subscriber,
 * which typically corresponds to one capapcity bundle. Relevant members are
 * declared final in the package scope.
 * 
 * @author Prashant Reddy
 */
public final class TariffSubscriberStructure
{
  enum AllocationMethod {
    TOTAL_ORDER, LOGIT_CHOICE
  }

  private final CustomerStructure customerStructure;
  private final CapacityBundle capacityBundle;

  final boolean benchmarkRiskEnabled;
  final double benchmarkRiskRatio;
  // final boolean tariffThrottlingEnabled;

  double inconvenienceWeight = 0.2;
  // inconvenience factors - all should be [0..1]
  double touFactor = 0.05;
  double interruptibilityFactor = 0.5;
  double variablePricingFactor = 0.7;
  double tieredRateFactor = 0.1;
  double tariffSwitchFactor = 0.1;
  double brokerSwitchFactor = 0.02;
  int expectedDuration = 14; // expected subscription duration, days
  double lambdaMax = 100.0; // convert [0..1] to [0..100]

  final Double expMeanPriceWeight;
  final Double maxValuePriceWeight;
  final Double realizedPriceWeight;
  final Double tariffVolumeThreshold = 20000.0;
  final AllocationMethod allocationMethod;
  final List<List<Double>> totalOrderRules = new ArrayList<List<Double>>();
  final double logitChoiceRationality;
  final int reconsiderationPeriod;
  final ProbabilityDistribution inertiaDistribution;
  final ProbabilityDistribution customerWealthDistribution;
  final double customerWealthReferenceMedian;
  final ProbabilityDistribution newTariffsExposure;
  final ProbabilityDistribution switchingDelay;
  final ProbabilityDistribution waitAfterSwitch;

  TariffSubscriberStructure (FactoredCustomerService service,
                             CustomerStructure structure,
                             CapacityBundle bundle,
                             Element xml)
  {
    customerStructure = structure;
    capacityBundle = bundle;

    Element constraintsElement =
      (Element) xml.getElementsByTagName("constraints").item(0);
    if (constraintsElement != null) {
      Element benchmarkRiskElement =
        (Element) constraintsElement.getElementsByTagName("benchmarkRisk")
                .item(0);
      if (benchmarkRiskElement != null) {
        benchmarkRiskEnabled =
          Boolean.parseBoolean(benchmarkRiskElement.getAttribute("enable"));
        double[][] ratio =
          ParserFunctions.parseMapToDoubleArray(benchmarkRiskElement
                  .getAttribute("ratio"));
        benchmarkRiskRatio = ratio[0][0] / ratio[0][1];
      }
      else {
        benchmarkRiskEnabled = false;
        benchmarkRiskRatio = Double.NaN;
      }
      // Element tariffThrottlingElement = (Element)
      // constraintsElement.getElementsByTagName("tariffThrottling").item(0);
      // if (tariffThrottlingElement != null) {
      // tariffThrottlingEnabled =
      // Boolean.parseBoolean(tariffThrottlingElement.getAttribute("enable"));
      // } else tariffThrottlingEnabled = false;
    }
    else
      throw new Error(
                      "Tariff subscriber constraints element must be included, even if empty.");

    Element influenceFactorsElement =
      (Element) xml.getElementsByTagName("influenceFactors").item(0);
    if (influenceFactorsElement != null) {
//      Element interruptibilityElement =
//        (Element) influenceFactorsElement
//                .getElementsByTagName("interruptibility").item(0);
//      if (interruptibilityElement != null) {
//        interruptibilityDiscount =
//          Double.parseDouble(interruptibilityElement.getAttribute("discount"));
//      }
//      else
//        interruptibilityDiscount = 0.0;
      // normalize inconvenience factors
      double divisor = (touFactor + interruptibilityFactor
              + variablePricingFactor + tieredRateFactor
              + tariffSwitchFactor + brokerSwitchFactor);
      touFactor /= divisor;
      interruptibilityFactor /= divisor;
      variablePricingFactor /= divisor;
      tieredRateFactor /= divisor;
      tariffSwitchFactor /= divisor;
      brokerSwitchFactor /= divisor;

      Element priceWeightsElement =
        (Element) influenceFactorsElement.getElementsByTagName("priceWeights")
                .item(0);
      if (priceWeightsElement != null) {
        expMeanPriceWeight =
          Double.parseDouble(priceWeightsElement.getAttribute("expMean"));
        maxValuePriceWeight =
          Double.parseDouble(priceWeightsElement.getAttribute("maxValue"));
        realizedPriceWeight =
          Double.parseDouble(priceWeightsElement.getAttribute("realized"));
      }
      else {
        expMeanPriceWeight = null;
        maxValuePriceWeight = null;
        realizedPriceWeight = null;
      }
    }
    else
      throw new Error(
                      "Tariff subscriber influence factors element must be included, even if empty.");

    Element allocationElement =
      (Element) xml.getElementsByTagName("allocation").item(0);
    allocationMethod =
      Enum.valueOf(AllocationMethod.class,
                   allocationElement.getAttribute("method"));
    if (allocationMethod == AllocationMethod.TOTAL_ORDER) {
      Element totalOrderElement =
        (Element) allocationElement.getElementsByTagName("totalOrder").item(0);
      populateTotalOrderRules(totalOrderElement.getAttribute("rules"));
      logitChoiceRationality = 1.0;
    }
    else {
      Element logitChoiceElement =
        (Element) allocationElement.getElementsByTagName("logitChoice").item(0);
      logitChoiceRationality = 
              Double.parseDouble(logitChoiceElement.
                                 getAttribute("rationality"));
    }

    Element reconsiderationElement =
      (Element) xml.getElementsByTagName("reconsideration").item(0);
    reconsiderationPeriod =
      Integer.parseInt(reconsiderationElement.getAttribute("period"));
    Element inertiaElement =
      (Element) xml.getElementsByTagName("switchingInertia").item(0);
    Node inertiaDistributionNode =
      inertiaElement.getElementsByTagName("inertiaDistribution").item(0);
    if (inertiaDistributionNode != null) {
      Element inertiaDistributionElement = (Element) inertiaDistributionNode;
      inertiaDistribution =
        new ProbabilityDistribution(service, inertiaDistributionElement);

      customerWealthDistribution = null;
      customerWealthReferenceMedian = 0.0;
      newTariffsExposure = null;
      switchingDelay = null;
      waitAfterSwitch = null;
    }
    else {
      inertiaDistribution = null;

      Node inertiaFactorsNode =
        inertiaElement.getElementsByTagName("inertiaFactors").item(0);
      if (inertiaFactorsNode == null) {
        throw new Error(
                        "TariffSubscriberStructure(): Inertia distribution and factors are both undefined!");
      }
      Element inertiaFactorsElement = (Element) inertiaFactorsNode;
      Element customerWealthElement =
        (Element) inertiaFactorsElement.getElementsByTagName("customerWealth")
                .item(0);
      customerWealthDistribution =
        new ProbabilityDistribution(service, customerWealthElement);
      customerWealthReferenceMedian =
        Double.parseDouble(customerWealthElement
                .getAttribute("referenceMedian"));
      Element newTariffsExposureElement =
        (Element) inertiaFactorsElement
                .getElementsByTagName("newTariffsExposure").item(0);
      newTariffsExposure =
        new ProbabilityDistribution(service, newTariffsExposureElement);
      Element switchingDelayElement =
        (Element) inertiaFactorsElement.getElementsByTagName("switchingDelay")
                .item(0);
      switchingDelay = new ProbabilityDistribution(service, switchingDelayElement);
      Element waitAfterSwitchElement =
        (Element) inertiaFactorsElement.getElementsByTagName("waitAfterSwitch")
                .item(0);
      waitAfterSwitch = new ProbabilityDistribution(service, waitAfterSwitchElement);
    }
  }

  private void populateTotalOrderRules (String config)
  {
    // example config:
    // "0.7:0.3, 0.5:0.3:0.2, 0.4:0.3:0.2:0.1, 0.4:0.3:0.2:0.05:0.05"
    // which yields the following rules:
    // size = 2, rule = [0.7, 0.3]
    // size = 3, rule = [0.5, 0.3, 0.2]
    // size = 4, rule = [0.4, 0.3, 0.2, 0.1]
    // size = 5, rule = [0.4, 0.3, 0.2, 0.05, 0.05]

    String[] rules = config.split(",");

    List<Double> degenerateRule = new ArrayList<Double>(1);
    degenerateRule.add(1.0);
    totalOrderRules.add(degenerateRule);

    for (int i = 0; i < rules.length; ++i) {
      if (rules[i].length() > 0) {
        String[] vals = rules[i].split(":");
        List<Double> rule = new ArrayList<Double>(vals.length);
        for (int j = 0; j < vals.length; ++j) {
          rule.add(Double.parseDouble(vals[j]));
        }
        totalOrderRules.add(rule);
      }
    }
  }

  CustomerStructure getCustomerStructure ()
  {
    return customerStructure;
  }

  CapacityBundle getCapacityBundle ()
  {
    return capacityBundle;
  }

} // end class
