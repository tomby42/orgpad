// Compiled by ClojureScript 0.0-2814 {}
goog.provide('orgpad.render.bezier');
goog.require('cljs.core');
orgpad.render.bezier.canvas = "mainCanvas";
orgpad.render.bezier.ctx = document.getElementById(orgpad.render.bezier.canvas).getContext("2d");
orgpad.render.bezier.norm = (function norm(pt1,pt2){

return Math.sqrt((((pt1.call(null,(0)) - pt2.call(null,(0))) * (pt1.call(null,(0)) - pt2.call(null,(0)))) + ((pt1.call(null,(1)) - pt2.call(null,(1))) * (pt1.call(null,(1)) - pt2.call(null,(1))))));
});
orgpad.render.bezier.midpoint = (function midpoint(pt1,pt2){
return (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[((pt1.call(null,(0)) + pt2.call(null,(0))) / (2)),((pt1.call(null,(1)) + pt2.call(null,(1))) / (2))],null));
});
orgpad.render.bezier.draw_cubic_bezier = (function draw_cubic_bezier(start,cp0,cp1,end){

orgpad.render.bezier.ctx.beginPath();

orgpad.render.bezier.ctx.moveTo(start.call(null,(0)),start.call(null,(1)));

orgpad.render.bezier.ctx.bezierCurveTo(cp0.call(null,(0)),cp0.call(null,(1)),cp1.call(null,(0)),cp1.call(null,(1)),end.call(null,(0)),end.call(null,(1)));

return orgpad.render.bezier.ctx.stroke();
});
orgpad.render.bezier.get_cpts_3pts = (function get_cpts_3pts(sharpness,start,mid,end){

var shift = cljs.core.map.call(null,cljs.core._,orgpad.render.bezier.midpoint.call(null,start,mid),orgpad.render.bezier.midpoint.call(null,mid,end));
var ratio = ((1) / ((1) + (orgpad.render.bezier.norm.call(null,start,mid) / orgpad.render.bezier.norm.call(null,mid,end))));
return (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[cljs.core.map.call(null,cljs.core._PLUS_,mid,(function (){var iter__4338__auto__ = ((function (shift,ratio){
return (function iter__12614(s__12615){
return (new cljs.core.LazySeq(null,((function (shift,ratio){
return (function (){
var s__12615__$1 = s__12615;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12615__$1);
if(temp__4126__auto__){
var s__12615__$2 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12615__$2)){
var c__4336__auto__ = cljs.core.chunk_first.call(null,s__12615__$2);
var size__4337__auto__ = cljs.core.count.call(null,c__4336__auto__);
var b__12617 = cljs.core.chunk_buffer.call(null,size__4337__auto__);
if((function (){var i__12616 = (0);
while(true){
if((i__12616 < size__4337__auto__)){
var x = cljs.core._nth.call(null,c__4336__auto__,i__12616);
cljs.core.chunk_append.call(null,b__12617,(((ratio - (1)) * x) * sharpness));

var G__12622 = (i__12616 + (1));
i__12616 = G__12622;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12617),iter__12614.call(null,cljs.core.chunk_rest.call(null,s__12615__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12617),null);
}
} else {
var x = cljs.core.first.call(null,s__12615__$2);
return cljs.core.cons.call(null,(((ratio - (1)) * x) * sharpness),iter__12614.call(null,cljs.core.rest.call(null,s__12615__$2)));
}
} else {
return null;
}
break;
}
});})(shift,ratio))
,null,null));
});})(shift,ratio))
;
return iter__4338__auto__.call(null,shift);
})()),cljs.core.map.call(null,cljs.core._,mid,(function (){var iter__4338__auto__ = ((function (shift,ratio){
return (function iter__12618(s__12619){
return (new cljs.core.LazySeq(null,((function (shift,ratio){
return (function (){
var s__12619__$1 = s__12619;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12619__$1);
if(temp__4126__auto__){
var s__12619__$2 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12619__$2)){
var c__4336__auto__ = cljs.core.chunk_first.call(null,s__12619__$2);
var size__4337__auto__ = cljs.core.count.call(null,c__4336__auto__);
var b__12621 = cljs.core.chunk_buffer.call(null,size__4337__auto__);
if((function (){var i__12620 = (0);
while(true){
if((i__12620 < size__4337__auto__)){
var x = cljs.core._nth.call(null,c__4336__auto__,i__12620);
cljs.core.chunk_append.call(null,b__12621,(((x * (-1)) * ratio) * sharpness));

var G__12623 = (i__12620 + (1));
i__12620 = G__12623;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12621),iter__12618.call(null,cljs.core.chunk_rest.call(null,s__12619__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12621),null);
}
} else {
var x = cljs.core.first.call(null,s__12619__$2);
return cljs.core.cons.call(null,(((x * (-1)) * ratio) * sharpness),iter__12618.call(null,cljs.core.rest.call(null,s__12619__$2)));
}
} else {
return null;
}
break;
}
});})(shift,ratio))
,null,null));
});})(shift,ratio))
;
return iter__4338__auto__.call(null,shift);
})())],null));
});
orgpad.render.bezier.get_cpts_curve = (function get_cpts_curve(sharpness,pts){

if((cljs.core.count.call(null,pts) >= (3))){
var cpts = orgpad.render.bezier.get_cpts_3pts.call(null,sharpness,cljs.core.peek.call(null,pts),cljs.core.peek.call(null,cljs.core.pop.call(null,pts)),cljs.core.peek.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,pts))));
return cljs.core.conj.call(null,get_cpts_curve.call(null,sharpness,cljs.core.pop.call(null,pts)),cljs.core.peek.call(null,cljs.core.pop.call(null,pts)),cpts.call(null,(0)),cpts.call(null,(1)));
} else {
return pts;
}
});
