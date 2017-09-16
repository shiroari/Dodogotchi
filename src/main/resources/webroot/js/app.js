
(function () {

  const URL = '/api/state'
  const TIME_INTERVAL = 10 * 60 * 1000;
  const MAX_HEALTH = 100;

  let fn = {};

  /// UI

  fn.initUI = function () {

    let dom = {

      body: document.getElementById(`content`),
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

    var toggleNightMode = function (dom) {
      let nightModeOn = dom.body.classList.toggle('nightmode');
      localStorage.setItem('nightmode', nightModeOn);
    };
  
    var restoreNightMode = function (dom) {
      let nightModeOn = localStorage.getItem('nightmode');
      if (nightModeOn === 'true') {
        dom.body.classList.add('nightmode');
      }
    };

    restoreNightMode(dom);

    dom.monster.addEventListener(`click`, e => {
      e.preventDefault();
      toggleNightMode(dom);
      dom.monster.style.marginTop = `-50px`;
      setTimeout(() => {
        dom.monster.style.marginTop = `0px`;
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
    return {
      monster: {
        type: '',
        status: '',
      },
      state: {
        hp: 0,
        level: 0,
        levelProgress: 0,
        message: ''
      }
    }
  }

  fn.updateModel = function (model, html) {

    fn.fetchState()
      .then((state) => fn.updateMonster({ state, monster: model.monster }))
      .then((m) => fn.updateView(m, html))
      .catch((err) => {
        console.log(err);
        model.state.disconnected = true
        fn.updateView(model, html);
      });

  };

  fn.fetchState = function () {
    return fetch(URL)
      .then((response) => response.json());
  }

  fn.updateMonster = function (model) {

    let monster = model.monster;
    let textArray = [];
    let randIndex = Math.floor(Math.random() * (3));

    if (monster.hp === MAX_HEALTH) {
      monster.status = `"Yippee!"`;
    } else if (monster.hp <= 99 && monster.hp >= 80) {
      textArray = [`"Oh yeah!"`, `"So happy!"`, `"Hi! Hello! Hi!"`];
      monster.status = textArray[randIndex];
    } else if (monster.hp <= 79 && monster.hp >= 60) {
      textArray = [`"You are the best!"`, `"Life is good!"`, `"<3"`];
      monster.status = textArray[randIndex];
    } else if (monster.hp <= 59 && monster.hp >= 40) {
      textArray = [`"Hmph!"`, `"Too. Many. Issues."`, `"Please close issues."`];
      monster.status = textArray[randIndex];
    } else if (monster.hp <= 39 && monster.hp >= 20) {
      textArray = [`"So mad at you!"`, `"%(*$&%&"`, `"Argh!"`];
      monster.status = textArray[randIndex];
    } else if (monster.hp <= 19 && monster.hp >= 1) {
      textArray = [`"Feeling sick..."`, `"Slipping away..."`, `"Stomachache..."`];
      monster.status = textArray[randIndex];
    } else if (monster.hp <= 0) {
      monster.status = `"R.I.P."`;
    }

    return model;
  };

  /// View

  fn.updateView = function (model, html) {
    fn.updateEvolutionUI(model.state, html.dom);
    fn.handleEvolutionTimer(model.state, html.dom);
    fn.setUI(model, html.dom);
    html.dom.preloader.style.display = `none`;
  };  

  /*
  * handleEvolutionTimer
  *
  * Start the interval timer that will be used to evolve based on tab count
  * over a period of time.
  */
  fn.handleEvolutionTimer = function (state, dom) {
    if (state.hp < 40) {
      const uiSegments = dom.evolutionUISegments;
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
  fn.updateEvolutionUI = function (state, dom) {
    const evolutionLevel = state.level;
    const numberOfSegments = state.levelProgress;
    const uiSegments = dom.evolutionUISegments;

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

    dom.evolutionSilhouettes.classList.remove(`level-0`);
    dom.evolutionSilhouettes.classList.remove(`level-1`);
    dom.evolutionSilhouettes.classList.add(`level-${state.level}`);
  };

  /*
  * setUI
  *
  * set the UI based on current state
  */
  fn.setUI = function (model, dom) {

    // handle hp update
    dom.monsterStatus.innerText = `${model.monster.status}`;

    dom.evolutionState.innerText = `${model.state.levelProgress}/10`;
    dom.hpProgressBar.style.width = `${model.state.hp}%`;
    dom.hpState.innerText = `${model.state.hp}/100`;

    if (model.state.disconnected) {
      dom.tabCount.innerText = 'No connection';
    } else if (model.state.message) {
      dom.tabCount.innerText = model.state.message;
    } else {
      dom.tabCount.innerText = '';
    }

    // set random monster asset from the variants
    fn.setRandomAsset(model.state, dom);

    // to poop or not to poop, that is the question
    fn.poop(model.state, dom);
  };

  /*
    * poop
    *
    * Based on current HP, randomly select and display poops for Tamagotchi.
    */
  fn.poop = function (state, dom) {

    const allPoops = [dom.poop.poop1, dom.poop.poop2, dom.poop.poop3,
    dom.poop.poop4, dom.poop.poop5, dom.poop.poop6];
    let displayPoops = [];

    // reset poops
    allPoops.forEach(elem => {
      elem.style.display = `none`;
    });

    // level 1 and level 2 have color thus we need colorized poops
    if (state.level > 0) {
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
  fn.setRandomAsset = function (state, dom) {

    const randIndex = Math.floor(Math.random() * 3);
    const stateAssets = {
      state1: [
        `assets/level-${state.level}/monster-state-1-v1.gif`,
        `assets/level-${state.level}/monster-state-1-v2.gif`,
        `assets/level-${state.level}/monster-state-1-v3.gif`
      ],
      state2: [
        `assets/level-${state.level}/monster-state-2-v1.gif`,
        `assets/level-${state.level}/monster-state-2-v2.gif`,
        `assets/level-${state.level}/monster-state-2-v3.gif`
      ],
      state3: [
        `assets/level-${state.level}/monster-state-3-v1.gif`,
        `assets/level-${state.level}/monster-state-3-v2.gif`,
        `assets/level-${state.level}/monster-state-3-v3.gif`
      ],
      state4: [
        `assets/level-${state.level}/monster-state-4-v1.gif`,
        `assets/level-${state.level}/monster-state-4-v2.gif`,
        `assets/level-${state.level}/monster-state-4-v3.gif`
      ],
      state5: [
        `assets/level-${state.level}/monster-state-5-v1.gif`,
        `assets/level-${state.level}/monster-state-5-v2.gif`,
        `assets/level-${state.level}/monster-state-5-v3.gif`
      ],
      state6: [
        `assets/level-${state.level}/monster-state-6-v1.gif`,
        `assets/level-${state.level}/monster-state-6-v1.gif`,
        `assets/level-${state.level}/monster-state-6-v1.gif`
      ]
    };

    monster.className = `monster--level-${state.level} monster--state-${randIndex}`;

    if (state.hp === 100) {
      // monster is full health
      dom.monster.src = stateAssets.state1[randIndex];
    } else if (state.hp <= 99 && state.hp >= 80) {
      // monster is content
      dom.monster.src = stateAssets.state1[randIndex];
    } else if (state.hp <= 79 && state.hp >= 60) {
      // monster is irritated
      dom.monster.src = stateAssets.state2[randIndex];
    } else if (state.hp <= 59 && state.hp >= 40) {
      // monster is angry
      dom.monster.src = stateAssets.state3[randIndex];
    } else if (state.hp <= 39 && state.hp >= 20) {
      // monster is sick
      dom.monster.src = stateAssets.state4[randIndex];
    } else if (state.hp <= 19 && state.hp >= 1) {
      // monster is dying
      dom.monster.src = stateAssets.state5[randIndex];
    } else if (state.hp <= 0) {
      // monster is dead (RIP)
      dom.monster.src = stateAssets.state6[randIndex];
    }
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