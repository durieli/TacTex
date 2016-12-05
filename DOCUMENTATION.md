# Documentation
---
The following is a high level documentation of the TacTex code.

### General Code Structure
TacTex's code can be found under src/main/java/edu/utexas/cs/tactex, containing the following files and modules:

* "core" directory: Provided by Power TAC developers and mostly unmodified by us, it contains the infrastructure for controlling the broker's computation flow and communication with the server. 

* "<service-name>Service" files: Spring services containing entry points to the main modules of the broker. A service typically processes messages that arrive (asyncronously) from the server in callback functions named "handle<msg-name>()". If a service implements the "Activatable" interface, its "activate()" function is invoked at every timeslot and invokes its corresponding module. Important services to look at:

..* ConfiguratorFactoryService: A special service that 1) configures the broker's parameters, and 2) plugs-in concrete classes that define the broker's runtime behaviors. To explore with different implemented strategies/components, comment/uncomment initialization lines in this file.
..* PortfolioManagerService: The main service responsible to the broker's behavior in the tariff market. This service extends the PortfolioManagerService template provided by Power TAC's sample broker.
..* MarketManagerService: The main service responsible to the broker's behavior in the wholesale market. This service extends the MarketManagerService template provided by Power TAC's sample broker.

* "interfaces" directory: Java interfaces. 

* "utilityestimation" directory: Contains classes implementing actions' utility-prediction, which is the core of TacTex's LATTE algorithm.

* "tariffoptimization" directory: Contains classes that optimize tariffs.

* "subscriptionspredictors" directory: Contains classes that predict customer subscriptions for candidate tariffs.

* "shiftingpredictors" directory: Contains classes for predicting how customers would shift consumption in response to Time-Of-Use tariffs.

* "costcurve" directory: Contains classes for predicting the cost curve of procuring energy in the wholesale market. 

* "servercustomers" directory: Contains code of factored-customers from the servers, used by TacTex.

* "utils" directory: Contains general utilities used by TacTex. 


### Flow of Control
The simulation progresses in timeslots, each representing 1 hour in the real world. Each timeslot takes 5 seconds in simulation. The sequence of event in each timeslots is roughly: 

* A TimeslotUpdate message sent from the server to all brokers (represented in the broker's runtime log, named broker1.trace, as an xml message named "timeslot-update").

* Server simulates events such as weather conditions and customers consumption/production, and sends corresponding messages to brokers. These messages are incepted by brokers in functions named "handle<msg-name>()". 

* When the server finishes the timeslot processing, it sends a <ts-done> message to the brokers (see broker runtime log, Broker1.trace), in response to which the broker sequentially calls the "activate()" functions in its Activatable services. Each "activate()" function activates one of the broker's modules and typically results in one or more actions sent back as messages to the server.  


### Tariff Publication Algorithms
One of the core components of TacTex is it's tariff publication algorithms. These algorithms are part of the core algorithm of the agent, named LATTE, 
algorithm:                                                                      
```
  portfoliomgr: selecttariffactions()
    utilityarchitectureactiongenerator: tariffoptimizer.optimizetariffs()
      tariffoptimizeroneshot:
        suggesttariffs
        computeshiftedenergy (for customer-tariff pairs)
        estimaterelevanttariffcharges
        estimateutilities
          predictutility
            predictcustomermigration
            estimateutility
      tariffoptimizerbinaryoneshot:
        suggesttariffs
        computeshiftedenergy (for customer-tariff pairs)
        estimaterelevanttariffcharges
        binarysearchoptimize (estimate utilities in binary search)
      tariffoptimizerincremental:
        tariffoptimizeroneshot.findfixedrateseed
        optimizerwrapper.findoptimum (e.g. gradientascent)
          setstepsize
          computederivatives
            evaluatepoint
              value
                computeshiftedenergy
                estimaterelevanttariffcharges
                estimateutilities (=> like above)
                  predictutility
                    predictcustomermigration
                    estimateutility
      tariffoptimizertoufixedmargin
        tariffoptimizeroneshot.findfixedrateseed
        computeshiftedenergy
        predictenergycosts
        createtarifffromfixedmargin
      tariffoptimizerrevoke
        removeeachtariff
        predictutility
          predictcustomermigration
          estimateutility
```


### More Documentation 
More information about the TacTex broker can be found in the following
references, that can be found in http://www.cs.utexas.edu/~urieli/:

1) "Autonomous Trading in Modern Electricity Markets"
Daniel Urieli
Ph.D. Dissertation, The University of Texas at Austin, Austin, Texas, USA, 2015.

2) "An MDP-Based Winning Approach to Autonomous Power Trading: Formalization and Empirical Analysis"
Daniel Urieli, Peter Stone
In Proc. of the 15th International Conference on Autonomous Agents and Multiagent Systems, 2016 (AAMAS-16).

3) "Autonomous Electricity Trading using Time-Of-Use Tariffs in a Competitive Market"
Daniel Urieli, Peter Stone
In Proc. of the 30th Conference on Artificial Intelligence, 2016 (AAAI-16).

4) "TacTex'13: A Champion Adaptive Power Trading Agent."
Daniel Urieli, Peter Stone
In Proc. of the 28th Conference on Artificial Intelligence, 2014 (AAAI-14).

