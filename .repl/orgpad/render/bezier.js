// Compiled by ClojureScript 0.0-2814 {}
goog.provide('orgpad.render.bezier');
goog.require('cljs.core');
orgpad.render.bezier.canvas = "mainCanvas";
orgpad.render.bezier.norm = (function norm(pt1,pt2){

return Math.sqrt((((pt1.call(null,(0)) - pt2.call(null,(0))) * (pt1.call(null,(0)) - pt2.call(null,(0)))) + ((pt1.call(null,(1)) - pt2.call(null,(1))) * (pt1.call(null,(1)) - pt2.call(null,(1))))));
});
orgpad.render.bezier.midpoint = (function midpoint(pt1,pt2){
return (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[((pt1.call(null,(0)) + pt2.call(null,(0))) / (2)),((pt1.call(null,(1)) + pt2.call(null,(1))) / (2))],null));
});
orgpad.render.bezier.draw_cubic_bezier = (function draw_cubic_bezier(pts){

var ctx = document.getElementById(orgpad.render.bezier.canvas).getContext("2d");
ctx.beginPath();

ctx.moveTo(cljs.core.nth.call(null,pts,(0)).call(null,(0)),cljs.core.nth.call(null,pts,(0)).call(null,(1)));

ctx.bezierCurveTo(cljs.core.nth.call(null,pts,(1)).call(null,(0)),cljs.core.nth.call(null,pts,(1)).call(null,(1)),cljs.core.nth.call(null,pts,(2)).call(null,(0)),cljs.core.nth.call(null,pts,(2)).call(null,(1)),cljs.core.nth.call(null,pts,(3)).call(null,(0)),cljs.core.nth.call(null,pts,(3)).call(null,(1)));

return ctx.stroke();
});
orgpad.render.bezier.get_cpts_3pts = (function get_cpts_3pts(sharpness,start,mid,end,direction){

var shift = cljs.core.map.call(null,cljs.core._,orgpad.render.bezier.midpoint.call(null,start,mid),orgpad.render.bezier.midpoint.call(null,mid,end));
var ratio = ((1) / ((1) + (orgpad.render.bezier.norm.call(null,start,mid) / orgpad.render.bezier.norm.call(null,mid,end))));
return cljs.core.into.call(null,cljs.core.PersistentVector.EMPTY,cljs.core.map.call(null,cljs.core._,mid,(function (){var iter__6280__auto__ = ((function (shift,ratio){
return (function iter__12564(s__12565){
return (new cljs.core.LazySeq(null,((function (shift,ratio){
return (function (){
var s__12565__$1 = s__12565;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12565__$1);
if(temp__4126__auto__){
var s__12565__$2 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12565__$2)){
var c__6278__auto__ = cljs.core.chunk_first.call(null,s__12565__$2);
var size__6279__auto__ = cljs.core.count.call(null,c__6278__auto__);
var b__12567 = cljs.core.chunk_buffer.call(null,size__6279__auto__);
if((function (){var i__12566 = (0);
while(true){
if((i__12566 < size__6279__auto__)){
var x = cljs.core._nth.call(null,c__6278__auto__,i__12566);
cljs.core.chunk_append.call(null,b__12567,(((x * direction) * ratio) * sharpness));

var G__12568 = (i__12566 + (1));
i__12566 = G__12568;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12567),iter__12564.call(null,cljs.core.chunk_rest.call(null,s__12565__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12567),null);
}
} else {
var x = cljs.core.first.call(null,s__12565__$2);
return cljs.core.cons.call(null,(((x * direction) * ratio) * sharpness),iter__12564.call(null,cljs.core.rest.call(null,s__12565__$2)));
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
return iter__6280__auto__.call(null,shift);
})()));
});
orgpad.render.bezier.cpts_closed_curve = (function cpts_closed_curve(sharpness,pts){

if((cljs.core.count.call(null,pts) >= (4))){
return cljs.core.conj.call(null,cpts_closed_curve.call(null,sharpness,cljs.core.pop.call(null,pts)),cljs.core._conj.call(null,cljs.core._conj.call(null,cljs.core._conj.call(null,cljs.core._conj.call(null,cljs.core.List.EMPTY,cljs.core.peek.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,pts)))),orgpad.render.bezier.get_cpts_3pts.call(null,sharpness,cljs.core.peek.call(null,cljs.core.pop.call(null,pts)),cljs.core.peek.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,pts))),cljs.core.peek.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,pts)))),(-1))),orgpad.render.bezier.get_cpts_3pts.call(null,sharpness,cljs.core.peek.call(null,pts),cljs.core.peek.call(null,cljs.core.pop.call(null,pts)),cljs.core.peek.call(null,cljs.core.pop.call(null,cljs.core.pop.call(null,pts))),(1))),cljs.core.peek.call(null,cljs.core.pop.call(null,pts))));
} else {
return cljs.core.PersistentVector.EMPTY;
}
});
orgpad.render.bezier.close_points = (function close_points(pts){

return cljs.core.conj.call(null,cljs.core.into.call(null,(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[cljs.core.peek.call(null,cljs.core.pop.call(null,pts)),cljs.core.peek.call(null,pts)],null)),pts),cljs.core.first.call(null,pts),cljs.core.first.call(null,cljs.core.rest.call(null,pts)));
});
orgpad.render.bezier.draw_closed_curve = (function() {
var draw_closed_curve = null;
var draw_closed_curve__1 = (function (pts){
orgpad.render.bezier.draw_cubic_bezier.call(null,cljs.core.peek.call(null,pts));

if(cljs.core.seq.call(null,pts)){
return draw_closed_curve.call(null,cljs.core.pop.call(null,pts));
} else {
return null;
}
});
var draw_closed_curve__2 = (function (sharpness,pts){
return draw_closed_curve.call(null,orgpad.render.bezier.cpts_closed_curve.call(null,sharpness,orgpad.render.bezier.close_points.call(null,pts)));
});
draw_closed_curve = function(sharpness,pts){
switch(arguments.length){
case 1:
return draw_closed_curve__1.call(this,sharpness);
case 2:
return draw_closed_curve__2.call(this,sharpness,pts);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
draw_closed_curve.cljs$core$IFn$_invoke$arity$1 = draw_closed_curve__1;
draw_closed_curve.cljs$core$IFn$_invoke$arity$2 = draw_closed_curve__2;
return draw_closed_curve;
})()
;
orgpad.render.bezier.test_pts_gen = (function test_pts_gen(){
return cljs.core.into.call(null,cljs.core.PersistentVector.EMPTY,(function (){var iter__6280__auto__ = (function iter__12573(s__12574){
return (new cljs.core.LazySeq(null,(function (){
var s__12574__$1 = s__12574;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12574__$1);
if(temp__4126__auto__){
var s__12574__$2 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12574__$2)){
var c__6278__auto__ = cljs.core.chunk_first.call(null,s__12574__$2);
var size__6279__auto__ = cljs.core.count.call(null,c__6278__auto__);
var b__12576 = cljs.core.chunk_buffer.call(null,size__6279__auto__);
if((function (){var i__12575 = (0);
while(true){
if((i__12575 < size__6279__auto__)){
var x = cljs.core._nth.call(null,c__6278__auto__,i__12575);
cljs.core.chunk_append.call(null,b__12576,(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(Math.random() * (300)),(Math.random() * (150))],null)));

var G__12577 = (i__12575 + (1));
i__12575 = G__12577;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12576),iter__12573.call(null,cljs.core.chunk_rest.call(null,s__12574__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12576),null);
}
} else {
var x = cljs.core.first.call(null,s__12574__$2);
return cljs.core.cons.call(null,(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(Math.random() * (300)),(Math.random() * (150))],null)),iter__12573.call(null,cljs.core.rest.call(null,s__12574__$2)));
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__6280__auto__.call(null,cljs.core.range.call(null,(1),(5)));
})());
});
orgpad.render.bezier.bezierTest = (function bezierTest(){
var ctx = document.getElementById(orgpad.render.bezier.canvas).getContext("2d");
var test_pts = orgpad.render.bezier.test_pts_gen.call(null);
console.log("Debug Bezier");

console.log("Colour changed");

var iter__6280__auto___12586 = ((function (ctx,test_pts){
return (function iter__12582(s__12583){
return (new cljs.core.LazySeq(null,((function (ctx,test_pts){
return (function (){
var s__12583__$1 = s__12583;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12583__$1);
if(temp__4126__auto__){
var s__12583__$2 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12583__$2)){
var c__6278__auto__ = cljs.core.chunk_first.call(null,s__12583__$2);
var size__6279__auto__ = cljs.core.count.call(null,c__6278__auto__);
var b__12585 = cljs.core.chunk_buffer.call(null,size__6279__auto__);
if((function (){var i__12584 = (0);
while(true){
if((i__12584 < size__6279__auto__)){
var x = cljs.core._nth.call(null,c__6278__auto__,i__12584);
cljs.core.chunk_append.call(null,b__12585,ctx.fillRect(orgpad.render.bezier.pts.call(null,x).call(null,(0)),orgpad.render.bezier.pts.call(null,x).call(null,(1)),(5),(5)));

var G__12587 = (i__12584 + (1));
i__12584 = G__12587;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12585),iter__12582.call(null,cljs.core.chunk_rest.call(null,s__12583__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12585),null);
}
} else {
var x = cljs.core.first.call(null,s__12583__$2);
return cljs.core.cons.call(null,ctx.fillRect(orgpad.render.bezier.pts.call(null,x).call(null,(0)),orgpad.render.bezier.pts.call(null,x).call(null,(1)),(5),(5)),iter__12582.call(null,cljs.core.rest.call(null,s__12583__$2)));
}
} else {
return null;
}
break;
}
});})(ctx,test_pts))
,null,null));
});})(ctx,test_pts))
;
iter__6280__auto___12586.call(null,cljs.core.range.call(null,(0),cljs.core.count.call(null,test_pts)));

return orgpad.render.bezier.draw_closed_curve.call(null,(1),test_pts);
});
