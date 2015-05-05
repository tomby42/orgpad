// Compiled by ClojureScript 0.0-2814 {}
goog.provide('adzerk.boot_cljs_repl');
goog.require('cljs.core');
goog.require('weasel.repl');
var repl_conn_11610 = null;
if(cljs.core.truth_((function (){var and__3570__auto__ = repl_conn_11610;
if(cljs.core.truth_(and__3570__auto__)){
return !(weasel.repl.alive_QMARK_.call(null));
} else {
return and__3570__auto__;
}
})())){
weasel.repl.connect.call(null,null);
} else {
}
