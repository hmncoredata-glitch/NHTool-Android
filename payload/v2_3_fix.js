/* NH Tool Stable v2.0.3 hotfix: mobile report layout + persistent image actions */
(function(){
  function installV203ReportFix(){
    const actions=document.getElementById('dailyActions');
    const filter=document.querySelector('#reportDailyPanel .nh-filter-row');
    if(actions&&filter){
      if(actions.previousElementSibling!==filter) filter.insertAdjacentElement('afterend',actions);
      actions.classList.remove('hidden');
      actions.classList.add('nh-daily-actions-fixed');
      const buttons=actions.querySelectorAll('button');
      if(buttons[0]) buttons[0].innerHTML='⬇ Lưu ảnh PNG';
      if(buttons[1]) buttons[1].innerHTML='↗ Chia sẻ ảnh';
    }
  }

  if(typeof renderDaily==='function'){
    const baseRenderDaily=renderDaily;
    renderDaily=function(date){
      const out=baseRenderDaily(date);
      installV203ReportFix();
      return out;
    };
  }
  if(typeof initReportHub==='function'){
    const baseInitReportHub=initReportHub;
    initReportHub=async function(){
      const out=await baseInitReportHub();
      installV203ReportFix();
      return out;
    };
  }
  if(typeof setReportMode==='function'){
    const baseSetReportMode=setReportMode;
    setReportMode=function(mode,scroll=true){
      const out=baseSetReportMode(mode,scroll);
      installV203ReportFix();
      return out;
    };
  }

  const style=document.createElement('style');
  style.id='nh-v203-report-fix';
  style.textContent=`
    #page-report,#page-report *{box-sizing:border-box;min-width:0}
    #page-report{width:100%;max-width:100%;overflow-x:hidden}
    .nh-report-overview,.nh-report-panel{width:100%;max-width:100%;overflow:hidden}
    .nh-dashboard-grid{width:100%;grid-template-columns:repeat(2,minmax(0,1fr))!important}
    .nh-stat-card{width:100%;max-width:100%;overflow:hidden}
    .nh-stat-card>div:not(.nh-stat-icon){min-width:0;max-width:100%;overflow:hidden}
    #dashboardGross,#dashboardNet{display:block;max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:clip}
    .report-actions.nh-daily-actions-fixed,.report-actions.nh-daily-actions-fixed.hidden{display:grid!important;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px;width:100%;margin:0 0 16px}
    .nh-daily-actions-fixed .btn{width:100%;min-height:46px;margin:0}
    @media(max-width:620px){
      .nh-report-overview{padding:12px!important}
      .nh-dashboard-grid{gap:8px!important}
      .nh-stat-card{padding:10px!important;gap:7px!important;min-height:86px!important}
      .nh-stat-icon{width:32px!important;height:32px!important;flex:0 0 32px!important;font-size:16px!important}
      .nh-stat-card small{font-size:10.5px!important;line-height:1.2!important}
      #dashboardGross,#dashboardNet{font-size:clamp(13px,3.75vw,17px)!important;letter-spacing:-.035em!important}
      #dashboardOrders{font-size:22px!important}
      .nh-deduction-row input{width:46px!important;font-size:20px!important}
      .nh-deduction-row b{font-size:18px!important}
      .nh-deduction-content em{font-size:9.5px!important;white-space:nowrap}
    }
    @media(max-width:360px){
      .nh-dashboard-grid{gap:6px!important}
      .nh-stat-card{padding:8px!important;gap:5px!important}
      .nh-stat-icon{width:28px!important;height:28px!important;flex-basis:28px!important;font-size:14px!important}
      #dashboardGross,#dashboardNet{font-size:12.5px!important}
    }
  `;
  document.head.appendChild(style);
  installV203ReportFix();
})();
