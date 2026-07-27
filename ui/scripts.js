/* =========================================================
   WaterReminder UI Prototype — Shared Interactions
   ========================================================= */

(function () {
  'use strict';

  // 打开/关闭通用遮罩与抽屉
  window.openDrawer = function () {
    document.querySelector('.drawer')?.classList.add('open');
    document.querySelector('.overlay')?.classList.add('open');
  };

  window.closeDrawer = function () {
    document.querySelector('.drawer')?.classList.remove('open');
    document.querySelector('.overlay')?.classList.remove('open');
  };

  window.openSheet = function (id) {
    const sheet = document.getElementById(id);
    if (!sheet) return;
    document.querySelector('.overlay')?.classList.add('open');
    sheet.classList.add('open');
  };

  window.closeSheet = function (id) {
    const sheet = id ? document.getElementById(id) : document.querySelector('.bottom-sheet.open');
    if (sheet) sheet.classList.remove('open');
    const anyOpen = document.querySelector('.bottom-sheet.open') || document.querySelector('.dialog.open');
    if (!anyOpen) document.querySelector('.overlay')?.classList.remove('open');
  };

  window.openDialog = function (id) {
    const dialog = document.getElementById(id);
    if (!dialog) return;
    document.querySelector('.overlay')?.classList.add('open');
    dialog.classList.add('open');
  };

  window.closeDialog = function (id) {
    const dialog = id ? document.getElementById(id) : document.querySelector('.dialog.open');
    if (dialog) dialog.classList.remove('open');
    const anyOpen = document.querySelector('.bottom-sheet.open') || document.querySelector('.dialog.open');
    if (!anyOpen) document.querySelector('.overlay')?.classList.remove('open');
  };

  window.toggleSwitch = function (el) {
    el.classList.toggle('on');
  };

  window.selectChip = function (el, group) {
    if (group) {
      document.querySelectorAll('[data-chip-group="' + group + '"]').forEach(function (c) {
        c.classList.remove('active');
        c.setAttribute('aria-pressed', 'false');
      });
    }
    el.classList.add('active');
    el.setAttribute('aria-pressed', 'true');
  };

  // Tab 切换（面板与标签在同一父容器下）
  window.initTabs = function (containerSelector) {
    const container = document.querySelector(containerSelector);
    if (!container) return;
    const tabs = container.querySelectorAll('.tab');
    const panels = container.parentElement ? container.parentElement.querySelectorAll('.tab-panel') : [];
    tabs.forEach(function (tab, index) {
      tab.addEventListener('click', function () {
        tabs.forEach(function (t) { t.classList.remove('active'); });
        panels.forEach(function (p) { p.classList.add('hidden'); });
        tab.classList.add('active');
        const target = tab.getAttribute('data-target');
        if (target) document.getElementById(target)?.classList.remove('hidden');
        else if (panels[index]) panels[index].classList.remove('hidden');
      });
    });
  };

  // 点击遮罩关闭所有浮层
  document.addEventListener('click', function (e) {
    if (e.target.classList.contains('overlay')) {
      document.querySelectorAll('.drawer').forEach(function (d) { d.classList.remove('open'); });
      document.querySelectorAll('.bottom-sheet').forEach(function (s) { s.classList.remove('open'); });
      document.querySelectorAll('.dialog').forEach(function (d) { d.classList.remove('open'); });
      e.target.classList.remove('open');
    }
  });

  // 返回按钮统一回首页
  document.querySelectorAll('[data-back-home]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      window.location.href = 'index.html';
    });
  });
})();
