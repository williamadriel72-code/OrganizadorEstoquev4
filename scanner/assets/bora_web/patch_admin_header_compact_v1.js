function bmCompactAdminMotoHeader(){
 const head=document.querySelector('.moto-list-head');
 if(!head)return;
 const refresh=document.getElementById('bmRefreshAdmin');
 const register=document.getElementById('bmRegisterMoto');
 if(refresh)refresh.textContent='↻ Atualizar';
 if(register)register.textContent='+ Cadastrar';
 let actions=head.querySelector('.bm-moto-head-actions');
 if(!actions){
  actions=document.createElement('div');
  actions.className='bm-moto-head-actions';
  head.appendChild(actions);
 }
 if(refresh&&refresh.parentElement!==actions)actions.appendChild(refresh);
 if(register&&register.parentElement!==actions)actions.appendChild(register);
}

(function bmInstallCompactHeaderStyles(){
 if(document.getElementById('bmCompactAdminHeaderStyle'))return;
 const style=document.createElement('style');
 style.id='bmCompactAdminHeaderStyle';
 style.textContent=`
 .moto-list{container-type:inline-size}
 .moto-list-head{
   display:grid!important;
   grid-template-columns:auto 1fr!important;
   grid-template-areas:'title count' 'actions actions';
   align-items:center!important;
   gap:7px 10px!important;
   padding:6px 7px 9px!important;
 }
 .moto-list-head>b{grid-area:title;font-size:15px!important;white-space:nowrap}
 .moto-list-head>.moto-count{grid-area:count;justify-self:end;font-size:11px!important;white-space:nowrap}
 .bm-moto-head-actions{grid-area:actions;display:flex;gap:6px;justify-content:flex-end;align-items:center;min-width:0}
 #bmRefreshAdmin,#bmRegisterMoto{
   width:auto!important;
   min-width:0!important;
   min-height:32px!important;
   margin:0!important;
   padding:6px 10px!important;
   border-radius:9px!important;
   font-size:11px!important;
   line-height:1!important;
   font-weight:800!important;
   white-space:nowrap!important;
   letter-spacing:0!important;
 }
 #bmRegisterMoto{background:#21c875!important;color:#06150d!important;border:1px solid #31d884!important}
 #bmRefreshAdmin{background:#2c3137!important;color:#fff!important;border:1px solid rgba(255,255,255,.08)!important}
 @container (min-width:430px){
   .moto-list-head{
     grid-template-columns:auto auto 1fr!important;
     grid-template-areas:'title count actions';
   }
   .moto-list-head>.moto-count{justify-self:start}
 }
 `;
 document.head.appendChild(style);
})();

const bmBindAdminBeforeCompactHeader=bindAdmin;
bindAdmin=function(){
 bmBindAdminBeforeCompactHeader();
 bmCompactAdminMotoHeader();
};
