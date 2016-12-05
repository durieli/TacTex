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
 *     Copyright (c) 2013 by the original author
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package edu.utexas.cs.tactex.servercustomers.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.log4j.Logger;
//import org.powertac.common.ConfigServerBroker;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Tariff;
import org.powertac.common.TariffEvaluationHelper;
import org.powertac.common.TimeService;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.spring.SpringApplicationContext;

import edu.utexas.cs.tactex.servercustomers.common.interfaces.CustomerModelAccessor;
import edu.utexas.cs.tactex.servercustomers.common.interfaces.TariffMarket;
import edu.utexas.cs.tactex.servercustomers.common.repo.TariffSubscriptionRepo;

/**
 * Tariff evaluation process intended to simplify customer models.
 * There should be one of these created for each CustomerInfo
 * instance within a customer model, since
 * tariff cost values are cached, and are dependent on PowerType.
 * 
 * @author John Collins
 */
public class TariffEvaluator
{
  static private Logger log = Logger.getLogger(TariffEvaluator.class.getName());

  // component dependencies
  TariffRepo tariffRepo;
  TariffMarket tariffMarket;
  TariffSubscriptionRepo tariffSubscriptionRepo;

  // access to customer model
  private CustomerModelAccessor accessor;
  private CustomerInfo customerInfo;

  // inconvenience factors
  private double touFactor = 0.2;
  private double tieredRateFactor = 0.1;
  private double variablePricingFactor = 0.5;
  private double interruptibilityFactor = 0.2;

  // amortization period for negative signup payments
  private int signupFeePeriod = 6;

  // profile cost analyzer
  private TariffEvaluationHelper helper;

  // per-customer parameter settings
  private int chunkSize = 1; // max size of allocation chunks
  private int maxChunkCount = 200; // max number of chunks
  private int tariffEvalDepth = 5; // # of tariffs/powerType to eval
  private boolean autonomous = false; //ConfigServerBroker.isAutonomous(); // false; // true; // false;
  private double inertia = 0.8;
  private double rationality = 0.9;
  private double inconvenienceWeight = 0.2;
  private double tariffSwitchFactor = 0.04;
  private double preferredDuration = 6;

  // state
  private int evaluationCounter = 0;
  private HashMap<Tariff, EvalData> evaluatedTariffs;
  private HashMap<Tariff, Integer> allocations;

  // algorithm parameters - needed for numerical stablity
  private double lambdaMax = 50.0;
  private double maxLinearUtility = 7.0;

  public TariffEvaluator (CustomerModelAccessor cma)
  {
    accessor = cma;
    customerInfo = cma.getCustomerInfo();
    helper = new TariffEvaluationHelper();
    evaluatedTariffs = new HashMap<Tariff, EvalData>();
    allocations = new HashMap<Tariff, Integer>();
    if (autonomous) {
      this.inertia = 0; // 0.8;
      this.rationality = 1.0; // 0.0; // 1.0; 
    }
  }

  // convenience method for logging support
  private String getName()
  {
    return customerInfo.getName();
  }

  /**
   * Delegates profile cost factors to helper.
   */
  public void initializeCostFactors (double wtExpected, double wtMax,
                                     double wtRealized, double soldThreshold)
  {
    helper.initializeCostFactors(wtExpected, wtMax, wtRealized, soldThreshold);
  }

  /**
   * Initializes the per-tariff inconvenience factors.
   * These are not normalized; it is up to the customer model to normalize the
   * per-tariff and cross-tariff factors as appropriate
   */
  public void initializeInconvenienceFactors (double touFactor,
                                              double tieredRateFactor,
                                              double variablePricingFactor,
                                              double interruptibilityFactor)
  {
    this.touFactor = touFactor;
    this.tieredRateFactor = tieredRateFactor;
    this.variablePricingFactor = variablePricingFactor;
    this.interruptibilityFactor = interruptibilityFactor;
  }

  /**
   * Initializes the per-timeslot regulation-capacity estimates.
   * All three represent per-timeslot estimates of exercised regulation
   * capacity, and are applicable only for tariffs with regulation rates.
   * Note that the expectedDischarge parameter only applies to
   * storage devices that can be discharged (batteries, pumped storage).
   * Values are from the customer's viewpoint, so curtailment and discharge
   * are negative (less energy for the customer) and down-regulation
   * is positive.
   * Default value for each of these factors is zero.
   */
  public void initializeRegulationFactors (double expectedCurtailment,
                                           double expectedDischarge,
                                           double expectedDownRegulation)
  {
    double expCurtail = expectedCurtailment;
    if (expCurtail > 0.0) {
      log.error(getName() + ": expectedCurtailment " + expCurtail
                + " must be non-positive");
      expCurtail = 0.0;
    }
    double expDis = expectedDischarge;
    if (expDis > 0.0) {
      log.error(getName() + ": expectedDischarge " + expDis
                + " must be non-positive");
      expDis = 0.0;
    }
    double expDown = expectedDownRegulation;
    if (expDown < 0.0) {
      log.error(getName() + ": expectedDownRegulation " + expDown
                + " must be non-negative");
      expDown = 0.0;
    }
    helper.initializeRegulationFactors(expCurtail, expDis, expDown);
  }

  // parameter settings
  /**
   * Sets the target size of allocation chunks. Default is 1. Actual
   * chunk size will be at least 0.5% of the population size.
   */
  public TariffEvaluator withChunkSize (int size)
  {
    if (size > 0)
      chunkSize = size;
    else
      log.error("chunk size " + size + " < 0");
    return this;
  }

  /**
   * Sets the number of tariffs/broker of each applicable PowerType
   * to consider. Default is 5, which means that only the 5 most recent
   * tariffs of each applicable PowerType from each broker are considered.
   * So if there are 10 brokers, and the PowerType is ELECTRIC_VEHICLE,
   * the applicable PowerTypes would be CONSUMPTION, INTERRUPTIBLE_CONSUMPTION,
   * and ELECTRIC_VEHICLE. If each broker has published at least five tariffs
   * for each of these types, the default value of 5 would result in evaluating
   * up to 150 alternative tariffs in addition to the currently subscribed
   * tariff(s).
   */
  public TariffEvaluator withTariffEvalDepth (int depth)
  {
    tariffEvalDepth = depth;
    return this;
  }
  
  /**
   * Sets the steady-state evaluation inertia for the customer. This is a
   * value in [0,1], where 0 is no inertia (always evaluates), and 1 is
   * total couch-potato inertia (never bothers to evaluate). The instantaneous
   * value starts at zero, so customers will have a chance to switch away
   * from the default tariff at the beginning of a sim, and is temporarily
   * set to zero when a tariff is revoked. Default value is 0.8.
   */
  public TariffEvaluator withInertia (double inertia)
  {
    this.inertia = inertia;
    if (autonomous) {
      this.inertia = 0;
    }
    return this;
  }

  /**
   * Sets the level of rationality for this customer.
   * Household customers are expected to have lower rationality than
   * business/industrial/institutional customers.
   * 1.0 is fully rational, 0.5 is quite irrational. A value of zero will
   * result in random choices. Default value is 0.9.
   */
  public TariffEvaluator withRationality (double rationality)
  {
    this.rationality = rationality;
    if (rationality < 0.0) {
      log.error("Rationality " + rationality + "< 0.0");
      this.rationality = 0.01;
    }
    else if (rationality > 1.0) {
      log.error("Rationality " + rationality + "> 1.0");
      this.rationality = 1.0;
    }
    if (autonomous) {
      this.rationality = 1.0; // 0.0; // 1.0;
    }
    return this;
  }

  /**
   * Sets the weight given to inconvenience (as opposed to cost)
   * in computing tariff utility. Must be
   * in the range [0,1]. Default value is 0.2.
   */
  public TariffEvaluator withInconvenienceWeight (double weight)
  {
    this.inconvenienceWeight = weight;
    return this;
  }

  /**
   * Sets the inconvenience of switching tariffs. Default value is 0.04.
   */
  public TariffEvaluator withTariffSwitchFactor(double factor)
  {
    this.tariffSwitchFactor = factor;
    return this;
  }

  /**
   * Sets the preferred maximum contract duration in days. For tariffs
   * having a non-zero early-withdraway fee, this is the period after which
   * the cost of withdrawal is discounted. It is also the standard period
   * over which usage cost is compared against signup/withdrawal payments.
   * Default value is 6 days.
   */
  public TariffEvaluator withPreferredContractDuration (double days)
  {
    this.preferredDuration  = days;
    return this;
  }

  /**
   * Evaluates tariffs and updates subscriptions
   * for a single customer model with a single power type.
   * Also handles tariff revoke/supersede. This requires that each
   * Customer model call this method once on each tariff publication cycle.
   */
//  public void evaluateTariffs ()
//  {
//    allocations.clear();
//    HashSet<Tariff> newTariffs =
//      new HashSet<Tariff>(getTariffRepo()
//              .findRecentActiveTariffs(tariffEvalDepth,
//                                       customerInfo.getPowerType()));
//
//    // make sure all superseding tariffs are in the set
//    addSupersedingTariffs(newTariffs);
//
//    // adjust inertia for BOG, accounting for the extra
//    // evaluation cycle at ts 0
//    double actualInertia =
//            Math.max(0.0,
//                     (1.0 - Math.pow(2, 1 - evaluationCounter)) * inertia);
//    evaluationCounter += 1;
//
//    // Get the cost eval for the appropriate default tariff
//    EvalData defaultEval = getDefaultTariffEval();
//    
//    // ensure we have the cost eval for each of the new tariffs
//    for (Tariff tariff : newTariffs) {
//      EvalData eval = evaluatedTariffs.get(tariff);
//      if (true || null == eval) { // BUG: if keeping old evals, better new tariffs can be considered worse than worse-ones with old evals
//        // compute the projected cost for this tariff
//        double cost = forecastCost(tariff);
//        double hassle = computeInconvenience(tariff);
//        log.info("Evaluated tariff " + tariff.getId()
//                 + ": cost=" + cost
//                 + ", inconvenience=" + hassle);
//        eval = new EvalData(cost, hassle);
//        evaluatedTariffs.put(tariff, eval);
//      }
//    }
//
//    // Iterate through the current active subscriptions
//    for (TariffSubscription subscription
//            : getTariffSubscriptionRepo().
//            findActiveSubscriptionsForCustomer(customerInfo)) {
//      Tariff subTariff = subscription.getTariff();
//      // find out how many of these customers can withdraw without penalty
//      double withdrawCost = subTariff.getEarlyWithdrawPayment(); 
//      int committedCount = subscription.getCustomersCommitted();
//      int expiredCount = subscription.getExpiredCustomerCount();
//      if (withdrawCost == 0.0 || expiredCount == committedCount) {
//        // no need to worry about expiration
//        evaluateAlternativeTariffs(subscription, actualInertia,
//                                   0.0, committedCount,
//                                   getDefaultTariff(),
//                                   defaultEval, newTariffs);
//      }
//      else {
//        // Evaluate expired and unexpired subsets separately
//        evaluateAlternativeTariffs(subscription, actualInertia,
//                                   0.0, expiredCount,
//                                   getDefaultTariff(),
//                                   defaultEval, newTariffs);
//        evaluateAlternativeTariffs(subscription, actualInertia,
//                                   withdrawCost, committedCount - expiredCount,
//                                   getDefaultTariff(), 
//                                   defaultEval, newTariffs);
//      }
//    }
//    updateSubscriptions();
//  }

  // Ensures that superseding tariffs are evaluated by adding them
  // to the newTariffs list
  private void addSupersedingTariffs (HashSet<Tariff> newTariffs)
  {
    List<TariffSubscription> revokedSubscriptions =
            getTariffSubscriptionRepo().getRevokedSubscriptionList(customerInfo);
    for (TariffSubscription sub : revokedSubscriptions) {
      Tariff supTariff = sub.getTariff().getIsSupersededBy();
      if (null != supTariff && supTariff.isSubscribable())
        newTariffs.add(supTariff);
    }
  }

  // evaluate alternatives
  private void evaluateAlternativeTariffs (TariffSubscription current,
                                           double inertia,
                                           double withdraw0,
                                           int population,
                                           Tariff defaultTariff,
                                           EvalData defaultEval,
                                           Set<Tariff> initialTariffs)
  {
    log.info("evaluateAlternativeTariffs(" + current.getTariff().getId() + ")");
    // Associate each alternate tariff with its utility value
    PriorityQueue<TariffUtility> evals = new PriorityQueue<TariffUtility>();
    HashSet<Tariff> tariffs = new HashSet<Tariff>(initialTariffs);
    tariffs.add(defaultTariff);

    // Check whether the current tariff is revoked, add it if not
    Tariff currentTariff = current.getTariff();
    boolean revoked = false;
    Tariff replacementTariff = null;
    if (currentTariff.getState() == Tariff.State.KILLED) {
      revoked = true;
      replacementTariff = currentTariff.getIsSupersededBy();
      log.info("Customer " + customerInfo.getName() + ": tariff "
               + currentTariff.getId() + " revoked, superseded by "
               + ((null == replacementTariff)
                       ? "default": replacementTariff.getId()));
      if (null == replacementTariff) {
        replacementTariff = defaultTariff;
      }
      //currentTariff = replacement;
      withdraw0 = 0.0; // withdraw without penalty
    }
    else
      tariffs.add(currentTariff);
    log.info("tariffs:");
    for (Tariff t : tariffs) {
      log.info(t.getId());
    }

    // for each tariff, including the current and default tariffs,
    // compute the utility
    for (Tariff tariff: tariffs) {
      log.info("UTILITYFORTARIFF " + tariff.getId());
      EvalData eval = evaluatedTariffs.get(tariff);
      double inconvenience = eval.inconvenience;
      double cost = eval.costEstimate;
      log.info("cost=" + cost + " inconvenience=" + inconvenience);
      if (tariff != currentTariff
              && tariff != replacementTariff) {
        if (!autonomous) {
          inconvenience += tariffSwitchFactor;
          //log.info("tariffSwitchFactor " + tariffSwitchFactor);
          if (tariff.getBroker() != currentTariff.getBroker()) {
            inconvenience +=
                accessor.getBrokerSwitchFactor(revoked);
          }
        }
        if (tariff.getSignupPayment() < 0.0) {
          // discount negative signup fees
          cost += tariff.getSignupPayment() *
              preferredDuration * 24.0 / signupFeePeriod;
        }
        else {
          cost += tariff.getSignupPayment();
        }
        cost += withdraw0;
        double withdrawFactor =
                Math.min(1.0,
                         (double)tariff.getMinDuration()
                         / (preferredDuration * TimeService.DAY));
        cost += withdrawFactor * tariff.getEarlyWithdrawPayment();
        log.info("withdraw0=" + withdraw0 + " withdrawFactor=" + withdrawFactor + " withdraw-cost=" + withdrawFactor * tariff.getEarlyWithdrawPayment());
      }
      // don't consider current tariff if it's revoked
      if (!revoked || tariff != currentTariff) {
        double utility = computeNormalizedDifference(cost,
                                                     defaultEval.costEstimate);
        utility -= inconvenienceWeight * inconvenience;
        log.info("adding TariffUtility(" + tariff.getId() + ", " + constrainUtility(utility) + " (" + utility + ")");
        evals.add(new TariffUtility(tariff, constrainUtility(utility)));
      }
    }
    
    // We now have utility values for each possible tariff.
    // Time to make some choices -
    // -- first, compute lambda from rationality
    // -- second, we have to compute the sum of transformed utilities
    
    if (autonomous && (rationality > 1 - 1e-6) ) { // enabling perfect rationality
      // find the maximum utility
      double maxUtil = -Double.MAX_VALUE;
      for (TariffUtility util : evals) {        
        if (util.utility > maxUtil)
          maxUtil = util.utility;
        log.info("utility " + util.utility + " maxUtil " + maxUtil);
      }      
      // in case there are more than max util - divide evenly
      int numMaxUtil = 0; 
      for (TariffUtility util : evals) {
        boolean isMaxUtil = util.utility == maxUtil; // > maxUtil - 1e-6;
        if (isMaxUtil) 
          numMaxUtil += 1;
      }
      // uniform probabilities to all with maxUtil value
      for (TariffUtility util : evals) {
        boolean isMaxUtil = util.utility == maxUtil; // > maxUtil - 1e-6;
        util.probability = isMaxUtil ? (1.0 / numMaxUtil) : 0;        
        log.info("utility " + util.utility + " probability " + util.probability);
      } 
  
      
    }
    else {
      double logitDenominator = 0.0;
      double lambda = Math.pow(lambdaMax, rationality) - 1.0;
      for (TariffUtility util : evals) {
        logitDenominator +=
                Math.exp(lambda * util.utility);
      }
      // then we can compute the probabilities
      for (TariffUtility util : evals) {
        util.probability =
                Math.exp(lambda * util.utility)
                / logitDenominator;
        //log.info("util " + util.probability + ", " + util.utility);
        if (Double.isNaN(util.probability)) {
          log.error("Probability NAN, util=" + util.utility
                    + ", denom=" + logitDenominator
                    + ", tariff " + util.tariff);
          util.probability = 0.0;
        }
      }
    }
    int remainingPopulation = population;
    int chunk = remainingPopulation;
    if (customerInfo.isMultiContracting()) {
      // Ideally, each individual customer makes a choice.
      // For large populations, we do it in chunks.
      chunk = getChunkSize(population);
    }
    while (remainingPopulation > 0) {
      int count = (int)Math.min(remainingPopulation, chunk);
      remainingPopulation -= count;
      // allocate a chunk
      double inertiaSample = accessor.getInertiaSample();
      if (!revoked && inertiaSample < inertia) {
        // skip this one if not processing revoked tariff
        continue;
      }
      double tariffSample = accessor.getTariffChoiceSample();
      // walk down the list until we run out of probability
      boolean allocated = false;
      for (TariffUtility tu : evals) {
        if (tariffSample <= tu.probability) {
          //log.info("addAllocation: " + tu.tariff.getId() + " " + tariffSample + " <= " + tu.probability);
          addAllocation(currentTariff, tu.tariff, count);
          allocated = true;
          break;
        }
        else {
          tariffSample -= tu.probability;
        }
      }
      if (!allocated) {
        log.error("Failed to allocate: P=" + tariffSample);
      }
    }
  }

  // Ensures numeric stability by constraining range of utility values.
  private double constrainUtility (double utility)
  {
    if (utility > maxLinearUtility) {
      double compressed = Math.log10(utility - maxLinearUtility);
      return Math.min(maxLinearUtility + compressed,
                      maxLinearUtility * 2);
    }
    else if (utility < -maxLinearUtility) {
      return -maxLinearUtility; // these will never be chosen anyway
    }
    else
      return utility;
  }

  // Computes the normalized difference between the cost of the default tariff
  // and the cost of a proposed tariff
  private double computeNormalizedDifference (double cost, double defaultCost)
  {
    double ndiff = (defaultCost - cost) / defaultCost;
    if (customerInfo.getPowerType().isProduction())
      ndiff = -ndiff;
    return ndiff;
  }

  // Retrieves default tariff
  private Tariff getDefaultTariff ()
  {
    return getTariffMarket().getDefaultTariff(customerInfo.getPowerType());
  }

//  private EvalData getDefaultTariffEval ()
//  {
//    Tariff defaultTariff = getDefaultTariff();
//    EvalData defaultEval = evaluatedTariffs.get(defaultTariff);
//    if (null == defaultEval) {
//      defaultEval =
//              new EvalData(forecastCost(defaultTariff),
//                           0.0);
//      evaluatedTariffs.put(defaultTariff, defaultEval);
//    }
//    return defaultEval;
//  }
//  
//  // Cost forecaster
//  private double forecastCost (Tariff tariff)
//  {
//    double[] profile = accessor.getCapacityProfile(tariff);
//    double profileCost = helper.estimateCost(tariff, profile);
//    double scale = preferredDuration * 24.0 / profile.length;
//    return profileCost * scale;
//  }

  // tracks additions and deletions for tariff subscriptions
  private void addAllocation (Tariff current, Tariff newTariff, int count)
  {
    if (current == newTariff)
      // ignore no-change allocations
      return;
    // decrement the old
    Integer ac = allocations.get(current);
    if (null == ac)
      ac = -count;
    else
      ac -= count;
    allocations.put(current, ac);
    // increment the new
    ac = allocations.get(newTariff);
    if (null == ac)
      // first time on this one
      ac = count;
    else
      ac += count;
    allocations.put(newTariff, ac);
  }
  
  // updates subscriptions based on computed allocations
  private void updateSubscriptions ()
  {
    int check = 0;
    for (Tariff tariff : allocations.keySet()) {
      int count = allocations.get(tariff);
      check += count;
      if (count < 0) {
        //unsubscribe
        TariffSubscription sub =
                getTariffSubscriptionRepo().findSubscriptionForTariffAndCustomer
                  (tariff, customerInfo);
        sub.unsubscribe(-count);
        log.info("customer " + customerInfo.getName()
                 + " unsubscribes " + -count
                 + " from tariff " + tariff.getId());
      }
      else if (count > 0) {
        // subscribe
        getTariffMarket().subscribeToTariff(tariff, customerInfo, count);
        log.info("customer " + customerInfo.getName()
                 + " subscribes " + count
                 + " to tariff " + tariff.getId());
      }
    }
    // sanity check
    if (check != 0) {
      log.error("Subscription updates do not add up for "
                + customerInfo.getName() + ": " + check);
    }
  }

  // inconvenience computation
  /**
   * Returns inconvenience of time-of-use rate.
   */
  public double getTouFactor ()
  {
    return touFactor;
  }

  /**
   * Returns inconvenience of tiered rate.
   */
  public double getTieredRateFactor ()
  {
    return tieredRateFactor;
  }

  /**
   * Returns inconvenience of variable pricing.
   */
  public double getVariablePricingFactor ()
  {
    return variablePricingFactor;
  }

  /**
   * Returns inconvenience of interruptibility.
   */
  public double getInterruptibilityFactor ()
  {
    return interruptibilityFactor;
  }

  /**
   * Computes composite per-tariff inconvenience of a tariff.
   */
  public double computeInconvenience (Tariff tariff)
  {
    double result = 0.0;
    // Time-of-use tariffs have multiple Rates, at least one of which
    // has a daily or weekly begin/end
    if (tariff.isTimeOfUse())
      result += touFactor;

    // Tiered tariffs have multiple Rates, at least one having
    // a non-zero tier threshold.
    if (tariff.isTiered())
      result += tieredRateFactor;

    // Variable-rate tariffs have at least one non-fixed Rate
    if (tariff.isVariableRate())
      result += variablePricingFactor;

    // Interruptible tariffs are for an interruptible PowerType, and
    // have a Rate with a maxCurtailment != 0
    if (tariff.isInterruptible())
      result += interruptibilityFactor;
    return result;
  }
  
  // returns the correct chunk size for a given population
  private int getChunkSize (int population)
  {
    if (population <= chunkSize)
      return population;
    else
      return Math.max(population / maxChunkCount, chunkSize);
  }

  // Spring component access ------------------------------------------
  private TariffRepo getTariffRepo ()
  {
    if (null != tariffRepo)
      return tariffRepo;
    tariffRepo =
            (TariffRepo) SpringApplicationContext.getBean("tariffRepo");
    return tariffRepo;
  }

  private TariffSubscriptionRepo getTariffSubscriptionRepo ()
  {
    if (null != tariffSubscriptionRepo)
      return tariffSubscriptionRepo;
    tariffSubscriptionRepo =
            (TariffSubscriptionRepo) SpringApplicationContext.getBean("tariffSubscriptionRepo");
    return tariffSubscriptionRepo;
  }

  private TariffMarket getTariffMarket ()
  {
    if (null != tariffMarket)
      return tariffMarket;
    tariffMarket =
            (TariffMarket) SpringApplicationContext.getBean("tariffMarketService");
    return tariffMarket;
  }

  // Container for tariff-utility recording ------------------------------
  class TariffUtility implements Comparable<TariffUtility>
  {
    Tariff tariff;
    double utility;
    double probability = 0.0;

    TariffUtility (Tariff tariff, double utility)
    {
      super();
      this.tariff = tariff;
      this.utility = utility;
    }

    @Override
    // natural ordering is by decreasing utility values
      public
      int compareTo (TariffUtility other)
    {
      double result = other.utility - utility;
      if (result == 0.0)
        // consistent with equals...
        return (int) (other.tariff.getId() - tariff.getId());
      else if (result > 0.0)
        return 1;
      else
        return -1;
    }
  }

  // Container for tariff-evaluation data
  class EvalData
  {
    double costEstimate;
    double inconvenience;

    EvalData (double cost, double inconvenience)
    {
      super();
      this.costEstimate = cost;
      this.inconvenience = inconvenience;
    }
  }
}
