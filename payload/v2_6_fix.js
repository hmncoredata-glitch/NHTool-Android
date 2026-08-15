/* NH Tool Stable v2.0.6: reliable chunked save/share bridge for large report files. */
(function(){
  function callChunkedNative(filename,mime,b64,mode){
    try{
      if(window.Android && typeof Android.beginTransfer==='function' && typeof Android.appendTransferChunk==='function'){
        let r=String(Android.beginTransfer(filename,mime));
        if(!r.startsWith('OK')) return r;
        const CHUNK=48*1024;
        for(let i=0;i<b64.length;i+=CHUNK){
          r=String(Android.appendTransferChunk(b64.slice(i,i+CHUNK)));
          if(!r.startsWith('OK')) return r;
        }
        r=mode==='share' ? String(Android.finishShareTransfer()) : String(Android.finishSaveTransfer());
        return r;
      }
      if(window.Android){
        if(mode==='share' && typeof Android.shareBase64==='function') return String(Android.shareBase64(filename,mime,b64));
        if(mode==='save' && typeof Android.saveBase64==='function') return String(Android.saveBase64(filename,mime,b64));
      }
    }catch(e){
      return 'ERROR:'+(e&&e.message?e.message:String(e));
    }
    return '';
  }

  saveBase64Native=function(filename,mime,b64){return callChunkedNative(filename,mime,b64,'save');};
  shareBase64Native=function(filename,mime,b64){return callChunkedNative(filename,mime,b64,'share');};

  downloadDailyPng=function(){
    if(!dailyData.length){toast('Chưa có dữ liệu báo cáo để lưu');return;}
    toast('Đang tạo và lưu ảnh...');
    try{
      const c=dailyCanvas(), b=c.toDataURL('image/png').split(',')[1], n=`bao_cao_giao_hang_${$('dailyDate').value}.png`;
      const r=saveBase64Native(n,'image/png',b);
      if(r && r.startsWith('OK')) toast('Đã lưu ảnh vào Thư viện / Pictures / NHTool');
      else if(r) toast(String(r).replace(/^ERROR:/,''));
      else toast('Không kết nối được chức năng lưu ảnh của Android');
    }catch(e){toast('Không lưu được ảnh: '+(e.message||e));}
  };

  shareDailyPng=function(){
    if(!dailyData.length){toast('Chưa có dữ liệu báo cáo để chia sẻ');return;}
    toast('Đang chuẩn bị ảnh để chia sẻ...');
    try{
      const c=dailyCanvas(), b=c.toDataURL('image/png').split(',')[1], n=`bao_cao_giao_hang_${$('dailyDate').value}.png`;
      const r=shareBase64Native(n,'image/png',b);
      if(r && r.startsWith('OK')) return;
      if(r) toast(String(r).replace(/^ERROR:/,''));
      else toast('Không kết nối được bảng chia sẻ Android');
    }catch(e){toast('Không chia sẻ được ảnh: '+(e.message||e));}
  };
})();
