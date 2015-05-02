// Compiled by ClojureScript 0.0-2814 {}
goog.provide('orgpad.render.metaball');
goog.require('cljs.core');
orgpad.render.metaball.norm_squared = (function norm_squared(pt1,pt2){
return (((pt1.call(null,(0)) - pt2.call(null,(0))) * (pt1.call(null,(0)) - pt2.call(null,(0)))) + ((pt1.call(null,(1)) - pt2.call(null,(1))) * (pt1.call(null,(1)) - pt2.call(null,(1)))));
});
orgpad.render.metaball.blob_fcion = (function blob_fcion(centre,pt,size){

return (size / orgpad.render.metaball.norm_squared.call(null,pt,centre));
});
orgpad.render.metaball.total_blob_fcion = (function total_blob_fcion(pt,blobs){
var temp = cljs.core.peek.call(null,blobs);
if(cljs.core.truth_(cljs.core.peek.call(null,blobs))){
return cljs.core.conj.call(null,total_blob_fcion.call(null,pt,cljs.core.pop.call(null,blobs)),orgpad.render.metaball.blob_fcion.call(null,cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"centre","centre",-948091970)),pt,cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007))));
} else {
return cljs.core.PersistentVector.EMPTY;
}
});
orgpad.render.metaball.resize_blobs = (function resize_blobs(size,blobs){
var temp = cljs.core.peek.call(null,blobs);
if(cljs.core.truth_(cljs.core.peek.call(null,blobs))){
return cljs.core.conj.call(null,resize_blobs.call(null,size,cljs.core.pop.call(null,blobs)),cljs.core.assoc.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007),(size * cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007)))));
} else {
return cljs.core.PersistentVector.EMPTY;
}
});
orgpad.render.metaball.shift = (function shift(centre1,centre2){

var norm = Math.sqrt(orgpad.render.metaball.norm_squared.call(null,centre1,centre2));
return (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(- (((20) * (centre1.call(null,(0)) - centre2.call(null,(0)))) / norm)),(((20) * (centre1.call(null,(1)) - centre2.call(null,(1)))) / norm)],null));
});
orgpad.render.metaball.get_coef = (function get_coef(i,j,blobs){

var correction = orgpad.render.metaball.shift.call(null,cljs.core.get.call(null,blobs.call(null,i),new cljs.core.Keyword(null,"centre","centre",-948091970)),cljs.core.get.call(null,blobs.call(null,j),new cljs.core.Keyword(null,"centre","centre",-948091970)));
var pt = (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(((cljs.core.first.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,i))) + cljs.core.first.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,j)))) / (2)) + correction.call(null,(0))),(((cljs.core.last.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,i))) + cljs.core.last.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,j)))) / (2)) + correction.call(null,(1)))],null));
return ((1) / cljs.core.reduce.call(null,cljs.core._PLUS_,orgpad.render.metaball.total_blob_fcion.call(null,pt,blobs)));
});
/**
* @param {...*} var_args
*/
orgpad.render.metaball.calc_blob = (function() { 
var calc_blob__delegate = function (blob_list){

var blobs = cljs.core.into.call(null,cljs.core.PersistentVector.EMPTY,blob_list);
var coef = cljs.core.reduce.call(null,cljs.core.max,(function (){var iter__4338__auto__ = ((function (blobs){
return (function iter__12022(s__12023){
return (new cljs.core.LazySeq(null,((function (blobs){
return (function (){
var s__12023__$1 = s__12023;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12023__$1);
if(temp__4126__auto__){
var xs__4624__auto__ = temp__4126__auto__;
var i = cljs.core.first.call(null,xs__4624__auto__);
var iterys__4334__auto__ = ((function (s__12023__$1,i,xs__4624__auto__,temp__4126__auto__,blobs){
return (function iter__12024(s__12025){
return (new cljs.core.LazySeq(null,((function (s__12023__$1,i,xs__4624__auto__,temp__4126__auto__,blobs){
return (function (){
var s__12025__$1 = s__12025;
while(true){
var temp__4126__auto____$1 = cljs.core.seq.call(null,s__12025__$1);
if(temp__4126__auto____$1){
var s__12025__$2 = temp__4126__auto____$1;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12025__$2)){
var c__4336__auto__ = cljs.core.chunk_first.call(null,s__12025__$2);
var size__4337__auto__ = cljs.core.count.call(null,c__4336__auto__);
var b__12027 = cljs.core.chunk_buffer.call(null,size__4337__auto__);
if((function (){var i__12026 = (0);
while(true){
if((i__12026 < size__4337__auto__)){
var j = cljs.core._nth.call(null,c__4336__auto__,i__12026);
if((i > j)){
cljs.core.chunk_append.call(null,b__12027,orgpad.render.metaball.get_coef.call(null,i,j,blobs));

var G__12028 = (i__12026 + (1));
i__12026 = G__12028;
continue;
} else {
var G__12029 = (i__12026 + (1));
i__12026 = G__12029;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12027),iter__12024.call(null,cljs.core.chunk_rest.call(null,s__12025__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12027),null);
}
} else {
var j = cljs.core.first.call(null,s__12025__$2);
if((i > j)){
return cljs.core.cons.call(null,orgpad.render.metaball.get_coef.call(null,i,j,blobs),iter__12024.call(null,cljs.core.rest.call(null,s__12025__$2)));
} else {
var G__12030 = cljs.core.rest.call(null,s__12025__$2);
s__12025__$1 = G__12030;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__12023__$1,i,xs__4624__auto__,temp__4126__auto__,blobs))
,null,null));
});})(s__12023__$1,i,xs__4624__auto__,temp__4126__auto__,blobs))
;
var fs__4335__auto__ = cljs.core.seq.call(null,iterys__4334__auto__.call(null,cljs.core.range.call(null,(0),cljs.core.count.call(null,blobs))));
if(fs__4335__auto__){
return cljs.core.concat.call(null,fs__4335__auto__,iter__12022.call(null,cljs.core.rest.call(null,s__12023__$1)));
} else {
var G__12031 = cljs.core.rest.call(null,s__12023__$1);
s__12023__$1 = G__12031;
continue;
}
} else {
return null;
}
break;
}
});})(blobs))
,null,null));
});})(blobs))
;
return iter__4338__auto__.call(null,cljs.core.range.call(null,(0),cljs.core.count.call(null,blobs)));
})());
cljs.core.print.call(null,coef,blobs);

return orgpad.render.metaball.resize_blobs.call(null,(function (){var x__3899__auto__ = (1);
var y__3900__auto__ = coef;
return ((x__3899__auto__ > y__3900__auto__) ? x__3899__auto__ : y__3900__auto__);
})(),blobs);
};
var calc_blob = function (var_args){
var blob_list = null;
if (arguments.length > 0) {
var G__12032__i = 0, G__12032__a = new Array(arguments.length -  0);
while (G__12032__i < G__12032__a.length) {G__12032__a[G__12032__i] = arguments[G__12032__i + 0]; ++G__12032__i;}
  blob_list = new cljs.core.IndexedSeq(G__12032__a,0);
} 
return calc_blob__delegate.call(this,blob_list);};
calc_blob.cljs$lang$maxFixedArity = 0;
calc_blob.cljs$lang$applyTo = (function (arglist__12033){
var blob_list = cljs.core.seq(arglist__12033);
return calc_blob__delegate(blob_list);
});
calc_blob.cljs$core$IFn$_invoke$arity$variadic = calc_blob__delegate;
return calc_blob;
})()
;
