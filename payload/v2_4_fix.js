/* NH Tool Stable v2.0.4: remove duplicate preview summary only. Exported image/PDF/Excel still keep totals. */
(function(){
  const style=document.createElement('style');
  style.id='nh-v204-preview-cleanup';
  style.textContent=`
    #dailyReportWrap .nh-report-summary,
    #monthlyPreview .nh-report-summary{display:none!important}
  `;
  document.head.appendChild(style);
})();
