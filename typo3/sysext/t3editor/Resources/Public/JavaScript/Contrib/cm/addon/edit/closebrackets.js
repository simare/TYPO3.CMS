!function(a){"object"==typeof exports&&"object"==typeof module?a(require("../../lib/codemirror")):"function"==typeof define&&define.amd?define(["../../lib/codemirror"],a):a(CodeMirror)}(function(a){function b(a,b){return"pairs"==b&&"string"==typeof a?a:"object"==typeof a&&null!=a[b]?a[b]:m[b]}function c(a){for(var b=0;b<a.length;b++){var c=a.charAt(b),e="'"+c+"'";o[e]||(o[e]=d(c))}}function d(a){return function(b){return i(b,a)}}function e(a){var b=a.state.closeBrackets;if(!b||b.override)return b;var c=a.getModeAt(a.getCursor());return c.closeBrackets||b}function f(c){var d=e(c);if(!d||c.getOption("disableInput"))return a.Pass;for(var f=b(d,"pairs"),g=c.listSelections(),h=0;h<g.length;h++){if(!g[h].empty())return a.Pass;var i=k(c,g[h].head);if(!i||f.indexOf(i)%2!=0)return a.Pass}for(var h=g.length-1;h>=0;h--){var j=g[h].head;c.replaceRange("",n(j.line,j.ch-1),n(j.line,j.ch+1),"+delete")}}function g(c){var d=e(c),f=d&&b(d,"explode");if(!f||c.getOption("disableInput"))return a.Pass;for(var g=c.listSelections(),h=0;h<g.length;h++){if(!g[h].empty())return a.Pass;var i=k(c,g[h].head);if(!i||f.indexOf(i)%2!=0)return a.Pass}c.operation(function(){var a=c.lineSeparator()||"\n";c.replaceSelection(a+a,null),c.execCommand("goCharLeft"),g=c.listSelections();for(var b=0;b<g.length;b++){var d=g[b].head.line;c.indentLine(d,null,!0),c.indentLine(d+1,null,!0)}})}function h(b){var c=a.cmpPos(b.anchor,b.head)>0;return{anchor:new n(b.anchor.line,b.anchor.ch+(c?-1:1)),head:new n(b.head.line,b.head.ch+(c?1:-1))}}function i(c,d){var f=e(c);if(!f||c.getOption("disableInput"))return a.Pass;var g=b(f,"pairs"),i=g.indexOf(d);if(i==-1)return a.Pass;for(var k,m=b(f,"triples"),o=g.charAt(i+1)==d,p=c.listSelections(),q=i%2==0,r=0;r<p.length;r++){var s,t=p[r],u=t.head,v=c.getRange(u,n(u.line,u.ch+1));if(q&&!t.empty())s="surround";else if(!o&&q||v!=d)if(o&&u.ch>1&&m.indexOf(d)>=0&&c.getRange(n(u.line,u.ch-2),u)==d+d&&(u.ch<=2||c.getRange(n(u.line,u.ch-3),n(u.line,u.ch-2))!=d))s="addFour";else if(o){var w=0==u.ch?" ":c.getRange(n(u.line,u.ch-1),u);if(a.isWordChar(v)||w==d||a.isWordChar(w))return a.Pass;s="both"}else{if(!q||c.getLine(u.line).length!=u.ch&&!j(v,g)&&!/\s/.test(v))return a.Pass;s="both"}else s=o&&l(c,u)?"both":m.indexOf(d)>=0&&c.getRange(u,n(u.line,u.ch+3))==d+d+d?"skipThree":"skip";if(k){if(k!=s)return a.Pass}else k=s}var x=i%2?g.charAt(i-1):d,y=i%2?d:g.charAt(i+1);c.operation(function(){if("skip"==k)c.execCommand("goCharRight");else if("skipThree"==k)for(var a=0;a<3;a++)c.execCommand("goCharRight");else if("surround"==k){for(var b=c.getSelections(),a=0;a<b.length;a++)b[a]=x+b[a]+y;c.replaceSelections(b,"around"),b=c.listSelections().slice();for(var a=0;a<b.length;a++)b[a]=h(b[a]);c.setSelections(b)}else"both"==k?(c.replaceSelection(x+y,null),c.triggerElectric(x+y),c.execCommand("goCharLeft")):"addFour"==k&&(c.replaceSelection(x+x+x+x,"before"),c.execCommand("goCharRight"))})}function j(a,b){var c=b.lastIndexOf(a);return c>-1&&c%2==1}function k(a,b){var c=a.getRange(n(b.line,b.ch-1),n(b.line,b.ch+1));return 2==c.length?c:null}function l(a,b){var c=a.getTokenAt(n(b.line,b.ch+1));return/\bstring/.test(c.type)&&c.start==b.ch&&(0==b.ch||!/\bstring/.test(a.getTokenTypeAt(b)))}var m={pairs:"()[]{}''\"\"",triples:"",explode:"[]{}"},n=a.Pos;a.defineOption("autoCloseBrackets",!1,function(d,e,f){f&&f!=a.Init&&(d.removeKeyMap(o),d.state.closeBrackets=null),e&&(c(b(e,"pairs")),d.state.closeBrackets=e,d.addKeyMap(o))});var o={Backspace:f,Enter:g};c(m.pairs+"`")});