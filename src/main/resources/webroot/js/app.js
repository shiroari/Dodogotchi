'use strict';

(function () {

  const TIME_INTERVAL = 5 * 60 * 1000;
  const EVOLUTION_INTERVAL = 24 * 60 * 60 * 1000;
  const EVOLUTION_HOUR = 9;
  const MAX_HEALTH = 100;

  const FATAL_DELAY_DAYS = 12;
  const WARN_DELAY_DAYS = 5;
  const NO_EVOLUTION_LEVEL = 5;
  const SURVIVAL_RATE = MAX_HEALTH / FATAL_DELAY_DAYS;

  // /rest/api/2/search?jql=(project=DR)and(status%20in%20("In%20Progress","Review","Test%20In%20Progress","Ready%20To%20Test"))and(issuetype!=epic)
  // /rest/api/2/search?jql=(project=PB)and(status%20in%20(%22To%20Do%22))
  //const URL = '/rest/api/2/search?jql=(project=DR)and(status%20in%20("In%20Progress","Review","Test%20In%20Progress","Ready%20To%20Test"))and(issuetype!=epic)'

  const URL = 'http://localhost:9090/state'

  let fn = {};

  /// UI

  fn.initUI = function () {

    let dom = {

      body: document.getElementById(`tabagotchi`),
      preloader: document.getElementById(`preloader-container`),
      evolutionState: document.getElementById(`evolution-state`),
      evolutionUISegments: document.querySelectorAll('.evolution-segment'),
      evolutionSilhouettes: document.getElementById('evolution-silhouettes'),
      hpProgressBar: document.getElementById(`hp-indicator`),
      hpState: document.getElementById(`hp-indicator-text`),
      poop: {
        poop1: document.getElementById(`poop-1`),
        poop2: document.getElementById(`poop-2`),
        poop3: document.getElementById(`poop-3`),
        poop4: document.getElementById(`poop-4`),
        poop5: document.getElementById(`poop-5`),
        poop6: document.getElementById(`poop-6`)
      },
      monster: document.querySelector(`#monster a img`),
      monsterStatus: document.getElementById(`monster-text`),
      tabCount: document.getElementById(`tab-count`),
      tips: document.querySelectorAll(`.tip`),
      tipModals: {
        evolution: document.getElementById(`evolution-tip`),
        health: document.getElementById(`health-tip`),
        monster: document.getElementById(`monster-tip`)
      }

    };

    dom.monster.addEventListener(`click`, e => {
      e.preventDefault();
      elements.monster.style.marginTop = `-50px`;
      setTimeout(() => {
        elements.monster.style.marginTop = `0px`;
      }, 250);
    });

    dom.tips.forEach(tip => {

      tip.addEventListener(`click`, e => {
        e.preventDefault();
      });

      tip.addEventListener(`mouseenter`, e => {
        e.preventDefault();
        const anchor = e.currentTarget;

        // prevent spamming
        if (anchor.classList.contains(`transition`)) {
          return;
        }

        if (anchor.classList.contains(`evolution`)) {
          elements.tipModals.evolution.style.display = `block`;
        } else if (anchor.classList.contains(`health`)) {
          elements.tipModals.health.style.display = `block`;
        } else {
          elements.tipModals.monster.style.display = `block`;
        }

        anchor.classList.add(`transition`);
      });

      tip.addEventListener(`mouseleave`, e => {
        e.preventDefault();
        const anchor = e.currentTarget;

        if (anchor.classList.contains(`evolution`)) {
          elements.tipModals.evolution.style.display = `none`;
        } else if (anchor.classList.contains(`health`)) {
          elements.tipModals.health.style.display = `none`;
        } else {
          elements.tipModals.monster.style.display = `none`;
        }

        anchor.classList.remove(`transition`);
      });
    });

    return { dom };
  };

  /// Model

  fn.initModel = function () {
    let startTime = new Date();
    startTime.setHours(EVOLUTION_HOUR);
    startTime.setMinutes(0);
    startTime.setSeconds(0);
    return {
      state: {
        hp: 0,
        evolutionLevel: 0,
        evolutionStage: 0,
        evolutionTimestamp: startTime,
        lastUpdatedAt: 0,
        monster: ``,
        monsterStatus: ``,
        penalty: 0,
        numIssues: 0,
        stagnation: true
      }
    }
  }

  fn.updateModel = function (model, html) {

    fn.fetchState()
      .then((event) => fn.handleEvent(model, event))
      .then((m) => fn.evolveModel(m))
      .then((m) => fn.updateAnimation(m))
      .then((m) => fn.updateView(m, html))
      .catch((err) => fn.updateView(model, html));

  };

  fn.fetchState = function () {
    var req = new Request(URL,
      { method: 'GET'
      //, credentials: 'include',
      });
    return fetch(req)
      .then((response) => response.json());
      //.then((resp) => fn.parseResponse(resp));
  }

  fn.parseResponse = function (json) {

    let now = Date.now()

    let issues = json.issues
      .filter((x) => x.fields.updated)
      .map((x) => x.fields.updated)
      .map((x) => Math.trunc((now - new Date(x)) / (24 * 60 * 60 * 1000)))
      .filter((x) => x > WARN_DELAY_DAYS)
      .sort((x, y) => x - y)

    if (issues.length === 0) {
      return {
        penalty: 0,
        numIssues: 0
      }
    }

    return {
      penalty: issues[issues.length - 1],
      numIssues: issues.length
    }
  };

  fn.handleEvent = function (model, event) {

    let state = model.state;
    let now = Date.now();

    state.penalty = event.penalty;
    state.numIssues = event.numIssues;
    state.hp = MAX_HEALTH - Math.trunc(SURVIVAL_RATE * state.penalty);
    state.stagnation = (state.penalty > NO_EVOLUTION_LEVEL);

    if (state.hp <= 0) {
      state.evolutionLevel = 0;
      state.evolutionStage = 0;
    }

    if (!state.stagnation
          && (state.lastUpdatedAt > 0)
          && (now - state.evolutionTimestamp >= EVOLUTION_INTERVAL)) {

      if (state.evolutionLevel < 2) {
        if (state.evolutionStage < 9) {
          state.evolutionStage++;
        } else if (state.evolutionStage === 9) {
          state.evolutionStage = 0;
          state.evolutionLevel++;
        }
      } else if (state.evolutionLevel === 2) {
        state.evolutionStage = 9;
      }

      state.evolutionTimestamp = Date.now();
    }

    state.lastUpdatedAt = Date.now();
    return model;
  };

  fn.updateAnimation = function (model) {

    let state = model.state;
    let textArray = [];
    let randIndex = Math.floor(Math.random() * (3));

    if (state.hp === MAX_HEALTH) {
      state.monsterStatus = `"Yippee!"`;
    } else if (state.hp <= 99 && state.hp >= 80) {
      textArray = [`"Oh yeah!"`, `"So happy!"`, `"Hi! Hello! Hi!"`];
      state.monsterStatus = textArray[randIndex];
    } else if (state.hp <= 79 && state.hp >= 60) {
      textArray = [`"You are the best!"`, `"Life is good!"`, `"<3"`];
      state.monsterStatus = textArray[randIndex];
    } else if (state.hp <= 59 && state.hp >= 40) {
      textArray = [`"Hmph!"`, `"Too. Many. Issues."`, `"Please close issues."`];
      state.monsterStatus = textArray[randIndex];
    } else if (state.hp <= 39 && state.hp >= 20) {
      textArray = [`"So mad at you!"`, `"%(*$&%&"`, `"Argh!"`];
      state.monsterStatus = textArray[randIndex];
    } else if (state.hp <= 19 && state.hp >= 1) {
      textArray = [`"Feeling sick..."`, `"Slipping away..."`, `"Stomachache..."`];
      state.monsterStatus = textArray[randIndex];
    } else if (state.hp <= 0) {
      state.monsterStatus = `"R.I.P."`;
    }

    return model;
  };

  /// View

  fn.updateView = function (model, html) {
    let state = model.state;
    fn.updateEvolutionUI(html.dom, state);
    fn.handleEvolutionTimer(html.dom, state);
    fn.setUI(html.dom, state);
    html.dom.preloader.style.display = `none`;
  };

  /*
  * poop
  *
  * Based on current HP, randomly select and display poops for Tamagotchi.
  */
  fn.poop = function (elements, state) {

    const allPoops = [elements.poop.poop1, elements.poop.poop2, elements.poop.poop3,
    elements.poop.poop4, elements.poop.poop5, elements.poop.poop6];
    let displayPoops = [];

    // reset poops
    allPoops.forEach(elem => {
      elem.style.display = `none`;
    });

    // level 1 and level 2 have color thus we need colorized poops
    if (state.evolutionLevel > 0) {
      allPoops.forEach(elem => {
        elem.classList.add(`color`);
      });
    }

    if (state.hp <= 59 && state.hp >= 40) {
      // monster is angry, show 4 poops
      displayPoops = allPoops.slice(0, 1);
    } else if (state.hp <= 39 && state.hp >= 20) {
      // monster is sick, show 5 poops
      displayPoops = allPoops.slice(0, 3);
    } else if (state.hp <= 19 && state.hp >= 1) {
      // monster is dying, show 6 poops
      // no need to reassign poops array if all shown
      displayPoops = allPoops;
    }

    // display the poops
    displayPoops.forEach(elem => {
      elem.style.display = `block`;
    });
  };

  /*
  * setRandomAsset
  *
  * Pick one of the three assets randomly, at each state. Diversified graphics to keep the
  * user entertained.
  */
  fn.setRandomAsset = function (elements, state) {

    const randIndex = Math.floor(Math.random() * 3);
    const stateAssets = {
      state1: [
        `assets/level-${state.evolutionLevel}/monster-state-1-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-1-v2.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-1-v3.gif`
      ],
      state2: [
        `assets/level-${state.evolutionLevel}/monster-state-2-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-2-v2.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-2-v3.gif`
      ],
      state3: [
        `assets/level-${state.evolutionLevel}/monster-state-3-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-3-v2.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-3-v3.gif`
      ],
      state4: [
        `assets/level-${state.evolutionLevel}/monster-state-4-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-4-v2.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-4-v3.gif`
      ],
      state5: [
        `assets/level-${state.evolutionLevel}/monster-state-5-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-5-v2.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-5-v3.gif`
      ],
      state6: [
        `assets/level-${state.evolutionLevel}/monster-state-6-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-6-v1.gif`,
        `assets/level-${state.evolutionLevel}/monster-state-6-v1.gif`
      ]
    };

    if (state.hp === 100) {
      // monster is full health
      elements.monster.src = stateAssets.state1[randIndex];
    } else if (state.hp <= 99 && state.hp >= 80) {
      // monster is content
      elements.monster.src = stateAssets.state1[randIndex];
    } else if (state.hp <= 79 && state.hp >= 60) {
      // monster is irritated
      elements.monster.src = stateAssets.state2[randIndex];
    } else if (state.hp <= 59 && state.hp >= 40) {
      // monster is angry
      elements.monster.src = stateAssets.state3[randIndex];
    } else if (state.hp <= 39 && state.hp >= 20) {
      // monster is sick
      //TODO: CHANGE THIS TO STATE 4
      elements.monster.src = stateAssets.state4[randIndex];
    } else if (state.hp <= 19 && state.hp >= 1) {
      // monster is dying
      //TODO: CHANGE THIS TO STATE 5
      elements.monster.src = stateAssets.state5[randIndex];
    } else if (state.hp <= 0) {
      // monster is dead (RIP)
      //TODO: CHANGE THIS TO STATE 6
      elements.monster.src = stateAssets.state6[randIndex];
    }
  };

  /*
  * handleEvolutionTimer
  *
  * Start the interval timer that will be used to evolve based on tab count
  * over a period of time.
  */
  fn.handleEvolutionTimer = function (elements, state) {
    if (state.stagnation) {
      const uiSegments = elements.evolutionUISegments;
      for (let idx = 0; idx < uiSegments.length; idx++) {
        uiSegments[idx].classList.remove(`toggle`);
      }
    }
  };

  /*
  * updateEvolutionUI
  *
  * Update evolution UI.
  */
  fn.updateEvolutionUI = function (elements, state) {
    const evolutionLevel = state.evolutionLevel;
    const numberOfSegments = state.evolutionStage;
    const uiSegments = elements.evolutionUISegments;

    // we either havent evolved or are on a new evolution stage. turn off all evolution segments.
    if (numberOfSegments === 0 && evolutionLevel < 2) {
      for (let idx = 0; idx < uiSegments.length; idx++) {
        uiSegments[idx].classList.remove(`on`);
        uiSegments[idx].classList.remove(`toggle`);
        uiSegments[idx].classList.add(`off`);
      }

      uiSegments[0].classList.add(`toggle`);
    } else {
      // we have evolved, turn on required number of segments.
      for (let idx = 0; idx <= numberOfSegments; idx++) {
        uiSegments[idx].classList.remove(`off`);
        uiSegments[idx].classList.remove(`toggle`);
        uiSegments[idx].classList.add(`on`);
      }

      if (numberOfSegments < 10 && evolutionLevel < 2) {
        uiSegments[numberOfSegments].classList.add(`toggle`);
      }
    }

    elements.evolutionSilhouettes.classList.remove(`level-0`);
    elements.evolutionSilhouettes.classList.remove(`level-1`);
    elements.evolutionSilhouettes.classList.add(`level-${state.evolutionLevel}`);

    // fn.setUI(elements, state);
  };

  /*
  * setUI
  *
  * set the UI based on current state
  */
  fn.setUI = function (elements, state) {

    const hp = state.hp <= 0 ? 0 : state.hp;

    // handle hp update
    elements.evolutionState.innerText = `${state.evolutionStage}/10`;
    elements.hpProgressBar.style.width = `${hp}%`;
    elements.hpState.innerText = `${hp}/100`;
    elements.monsterStatus.innerText = `${state.monsterStatus}`;
    elements.tabCount.innerText = `You have ${state.numIssues} overdue issues (>${WARN_DELAY_DAYS}Days).\nMaximum ${state.penalty} days`;

    // set random monster asset from the variants
    fn.setRandomAsset(elements, state);

    // to poop or not to poop, that is the question
    fn.poop(elements, state);
  };

  /// init

  fn.loop = function (model, html) {
    fn.updateModel(model, html);
    setTimeout(() => {
        fn.loop(model, html)
      }, TIME_INTERVAL);
  };

  let html = fn.initUI();
  let model = fn.initModel();

  fn.loop(model, html);

  //document.addEventListener(`DOMContentLoaded`, fn.initUI);

}());