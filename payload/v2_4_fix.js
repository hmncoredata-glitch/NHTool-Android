/* NH Tool Stable v2.0.4: clean preview summary + truthful Android save/share feedback. */
(function(){
  const style=document.createElement('style');
  style.id='nh-v204-preview-cleanup';
  style.textContent=`
    #dailyReportWrap .nh-report-summary,
    #monthlyPreview .nh-report-summary{display:none!important}
  `;
  document.head.appendChild(style);

  downloadDailyPng=function(){
    if(!dailyData.length){toast('Chưa có dữ liệu báo cáo để lưu');return;}
    toast('Đang lưu ảnh...');
    const c=dailyCanvas(),b=c.toDataURL('image/png').split(',')[1],n=`bao_cao_giao_hang_${$('dailyDate').value}.png`,r=saveBase64Native(n,'image/png',b);
    if(!r){fallbackDownload(n,'image/png',b);toast('Thiết bị không hỗ trợ lưu ảnh trực tiếp');}
    else if(r.startsWith('OK')) toast('Đã lưu ảnh vào Thư viện / album NHTool');
    else toast(String(r).replace(/^ERROR:/,''));
  };

  shareDailyPng=function(){
    if(!dailyData.length){toast('Chưa có dữ liệu báo cáo để chia sẻ');return;}
    toast('Đang mở bảng chia sẻ...');
    const c=dailyCanvas(),b=c.toDataURL('image/png').split(',')[1],n=`bao_cao_giao_hang_${$('dailyDate').value}.png`,r=shareBase64Native(n,'image/png',b);
    if(!r) toast('Thiết bị không mở được bảng chia sẻ');
    else if(!r.startsWith('OK')) toast(String(r).replace(/^ERROR:/,''));
  };
})();
