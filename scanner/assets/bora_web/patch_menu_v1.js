function bmInstallMainMenuButton(){
 const head=document.querySelector('.admin-head');
 if(!head||document.getElementById('bmMainMenu'))return;
 const logout=document.getElementById('logout');
 const btn=document.createElement('button');
 btn.id='bmMainMenu';
 btn.className='btn secondary small';
 btn.textContent='← MENU PRINCIPAL';
 btn.style.marginLeft='auto';
 btn.addEventListener('click',()=>{
  adminState.selectedMoto=null;
  adminState.selectedBairro=null;
  drawAdmin();
 });
 if(logout){
  logout.style.marginLeft='8px';
  head.insertBefore(btn,logout);
 }else{
  head.appendChild(btn);
 }
}

const bmBindAdminWithMainMenu=bindAdmin;
bindAdmin=function(){
 bmBindAdminWithMainMenu();
 bmInstallMainMenuButton();
};
