(function(t){function n(n){for(var o,c,a=n[0],u=n[1],l=n[2],f=0,p=[];f<a.length;f++)c=a[f],r[c]&&p.push(r[c][0]),r[c]=0;for(o in u)Object.prototype.hasOwnProperty.call(u,o)&&(t[o]=u[o]);s&&s(n);while(p.length)p.shift()();return i.push.apply(i,l||[]),e()}function e(){for(var t,n=0;n<i.length;n++){for(var e=i[n],o=!0,a=1;a<e.length;a++){var u=e[a];0!==r[u]&&(o=!1)}o&&(i.splice(n--,1),t=c(c.s=e[0]))}return t}var o={},r={app:0},i=[];function c(n){if(o[n])return o[n].exports;var e=o[n]={i:n,l:!1,exports:{}};return t[n].call(e.exports,e,e.exports,c),e.l=!0,e.exports}c.m=t,c.c=o,c.d=function(t,n,e){c.o(t,n)||Object.defineProperty(t,n,{enumerable:!0,get:e})},c.r=function(t){"undefined"!==typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(t,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(t,"__esModule",{value:!0})},c.t=function(t,n){if(1&n&&(t=c(t)),8&n)return t;if(4&n&&"object"===typeof t&&t&&t.__esModule)return t;var e=Object.create(null);if(c.r(e),Object.defineProperty(e,"default",{enumerable:!0,value:t}),2&n&&"string"!=typeof t)for(var o in t)c.d(e,o,function(n){return t[n]}.bind(null,o));return e},c.n=function(t){var n=t&&t.__esModule?function(){return t["default"]}:function(){return t};return c.d(n,"a",n),n},c.o=function(t,n){return Object.prototype.hasOwnProperty.call(t,n)},c.p="/";var a=window["webpackJsonp"]=window["webpackJsonp"]||[],u=a.push.bind(a);a.push=n,a=a.slice();for(var l=0;l<a.length;l++)n(a[l]);var s=u;i.push([0,"chunk-vendors"]),e()})({0:function(t,n,e){t.exports=e("56d7")},"034f":function(t,n,e){"use strict";var o=e("04f5"),r=e.n(o);r.a},"04f5":function(t,n,e){},"56d7":function(t,n,e){"use strict";e.r(n);e("cadf"),e("551c");var o=e("2b0e"),r=function(){var t=this,n=t.$createElement,o=t._self._c||n;return o("div",{attrs:{id:"app"}},[o("img",{attrs:{src:e("cf05")}}),o("div",{staticClass:"org-name"},[t._v(t._s(t.state.name))]),o("table",[t._m(0),o("tbody",t._l(t.state.balances,function(n,e){return o("tr",[o("td",[t._v(t._s(e))]),o("td",[t._v("£"+t._s(n))])])}))])])},i=[function(){var t=this,n=t.$createElement,e=t._self._c||n;return e("thead",[e("tr",[e("td",[t._v("Account")]),e("td",[t._v("Amount")])])])}],c=e("9ef7"),a={name:"app",data:function(){return{state:{name:"Init",balances:{John:"100.00"}}}},mounted:function(){var t=window.location.protocol+"//"+window.location.host+"/api/";console.log("connect to",t),this.proxy=new c["Proxy"]({url:t},this.onOpen,this.onClose,this.onError),window.proxy=this.proxy},methods:{onOpen:function(){var t=this;console.log("braid connected",this.proxy),this.proxy.bank.getInitialState().then(function(n){console.log("initial state",n),t.state=n}).catch(function(t){console.log("failed to get initial state",t)})},onClose:function(){console.log("braid closed")},onError:function(t){console.error("connection error",t)}}},u=a,l=(e("034f"),e("2877")),s=Object(l["a"])(u,r,i,!1,null,null,null),f=s.exports;o["a"].config.productionTip=!1,new o["a"]({render:function(t){return t(f)}}).$mount("#app")},cf05:function(t,n,e){t.exports=e.p+"img/logo.82b9c7a5.png"}});
//# sourceMappingURL=app.0e1dbd1d.js.map