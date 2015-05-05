// Compiled by ClojureScript 0.0-2814 {}
goog.provide('adzerk.boot_reload.reload');
goog.require('cljs.core');
goog.require('goog.net.jsloader');
goog.require('goog.async.DeferredList');
goog.require('goog.Uri');
goog.require('clojure.string');
adzerk.boot_reload.reload.page_uri = (new goog.Uri(window.location.href));
adzerk.boot_reload.reload.ends_with_QMARK_ = (function ends_with_QMARK_(s,pat){
return cljs.core._EQ_.call(null,pat,cljs.core.subs.call(null,s,(cljs.core.count.call(null,s) - cljs.core.count.call(null,pat))));
});
adzerk.boot_reload.reload.reload_page_BANG_ = (function reload_page_BANG_(){
return window.location.reload();
});
adzerk.boot_reload.reload.changed_href_QMARK_ = (function changed_href_QMARK_(href_or_uri,changed){
if(cljs.core.truth_(href_or_uri)){
var uri = (new goog.Uri(href_or_uri));
var path = adzerk.boot_reload.reload.page_uri.resolve(uri).getPath();
if(cljs.core.truth_(cljs.core.not_empty.call(null,cljs.core.filter.call(null,((function (uri,path){
return (function (p1__5371_SHARP_){
return adzerk.boot_reload.reload.ends_with_QMARK_.call(null,p1__5371_SHARP_,path);
});})(uri,path))
,changed)))){
return uri;
} else {
return null;
}
} else {
return null;
}
});
adzerk.boot_reload.reload.reload_css = (function reload_css(changed){
var sheets = document.styleSheets;
var seq__5376 = cljs.core.seq.call(null,cljs.core.range.call(null,(0),sheets.length));
var chunk__5377 = null;
var count__5378 = (0);
var i__5379 = (0);
while(true){
if((i__5379 < count__5378)){
var s = cljs.core._nth.call(null,chunk__5377,i__5379);
var temp__4126__auto___5380 = (sheets[s]);
if(cljs.core.truth_(temp__4126__auto___5380)){
var sheet_5381 = temp__4126__auto___5380;
var temp__4126__auto___5382__$1 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,sheet_5381.href,changed);
if(cljs.core.truth_(temp__4126__auto___5382__$1)){
var href_uri_5383 = temp__4126__auto___5382__$1;
sheet_5381.ownerNode.href = href_uri_5383.makeUnique().toString();
} else {
}
} else {
}

var G__5384 = seq__5376;
var G__5385 = chunk__5377;
var G__5386 = count__5378;
var G__5387 = (i__5379 + (1));
seq__5376 = G__5384;
chunk__5377 = G__5385;
count__5378 = G__5386;
i__5379 = G__5387;
continue;
} else {
var temp__4126__auto__ = cljs.core.seq.call(null,seq__5376);
if(temp__4126__auto__){
var seq__5376__$1 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5376__$1)){
var c__4369__auto__ = cljs.core.chunk_first.call(null,seq__5376__$1);
var G__5388 = cljs.core.chunk_rest.call(null,seq__5376__$1);
var G__5389 = c__4369__auto__;
var G__5390 = cljs.core.count.call(null,c__4369__auto__);
var G__5391 = (0);
seq__5376 = G__5388;
chunk__5377 = G__5389;
count__5378 = G__5390;
i__5379 = G__5391;
continue;
} else {
var s = cljs.core.first.call(null,seq__5376__$1);
var temp__4126__auto___5392__$1 = (sheets[s]);
if(cljs.core.truth_(temp__4126__auto___5392__$1)){
var sheet_5393 = temp__4126__auto___5392__$1;
var temp__4126__auto___5394__$2 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,sheet_5393.href,changed);
if(cljs.core.truth_(temp__4126__auto___5394__$2)){
var href_uri_5395 = temp__4126__auto___5394__$2;
sheet_5393.ownerNode.href = href_uri_5395.makeUnique().toString();
} else {
}
} else {
}

var G__5396 = cljs.core.next.call(null,seq__5376__$1);
var G__5397 = null;
var G__5398 = (0);
var G__5399 = (0);
seq__5376 = G__5396;
chunk__5377 = G__5397;
count__5378 = G__5398;
i__5379 = G__5399;
continue;
}
} else {
return null;
}
}
break;
}
});
adzerk.boot_reload.reload.reload_img = (function reload_img(changed){
var images = document.images;
var seq__5404 = cljs.core.seq.call(null,cljs.core.range.call(null,(0),images.length));
var chunk__5405 = null;
var count__5406 = (0);
var i__5407 = (0);
while(true){
if((i__5407 < count__5406)){
var s = cljs.core._nth.call(null,chunk__5405,i__5407);
var temp__4126__auto___5408 = (images[s]);
if(cljs.core.truth_(temp__4126__auto___5408)){
var image_5409 = temp__4126__auto___5408;
var temp__4126__auto___5410__$1 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,image_5409.src,changed);
if(cljs.core.truth_(temp__4126__auto___5410__$1)){
var href_uri_5411 = temp__4126__auto___5410__$1;
image_5409.src = href_uri_5411.makeUnique().toString();
} else {
}
} else {
}

var G__5412 = seq__5404;
var G__5413 = chunk__5405;
var G__5414 = count__5406;
var G__5415 = (i__5407 + (1));
seq__5404 = G__5412;
chunk__5405 = G__5413;
count__5406 = G__5414;
i__5407 = G__5415;
continue;
} else {
var temp__4126__auto__ = cljs.core.seq.call(null,seq__5404);
if(temp__4126__auto__){
var seq__5404__$1 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5404__$1)){
var c__4369__auto__ = cljs.core.chunk_first.call(null,seq__5404__$1);
var G__5416 = cljs.core.chunk_rest.call(null,seq__5404__$1);
var G__5417 = c__4369__auto__;
var G__5418 = cljs.core.count.call(null,c__4369__auto__);
var G__5419 = (0);
seq__5404 = G__5416;
chunk__5405 = G__5417;
count__5406 = G__5418;
i__5407 = G__5419;
continue;
} else {
var s = cljs.core.first.call(null,seq__5404__$1);
var temp__4126__auto___5420__$1 = (images[s]);
if(cljs.core.truth_(temp__4126__auto___5420__$1)){
var image_5421 = temp__4126__auto___5420__$1;
var temp__4126__auto___5422__$2 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,image_5421.src,changed);
if(cljs.core.truth_(temp__4126__auto___5422__$2)){
var href_uri_5423 = temp__4126__auto___5422__$2;
image_5421.src = href_uri_5423.makeUnique().toString();
} else {
}
} else {
}

var G__5424 = cljs.core.next.call(null,seq__5404__$1);
var G__5425 = null;
var G__5426 = (0);
var G__5427 = (0);
seq__5404 = G__5424;
chunk__5405 = G__5425;
count__5406 = G__5426;
i__5407 = G__5427;
continue;
}
} else {
return null;
}
}
break;
}
});
adzerk.boot_reload.reload.reload_js = (function reload_js(changed,p__5430){
var map__5432 = p__5430;
var map__5432__$1 = ((cljs.core.seq_QMARK_.call(null,map__5432))?cljs.core.apply.call(null,cljs.core.hash_map,map__5432):map__5432);
var on_jsload = cljs.core.get.call(null,map__5432__$1,new cljs.core.Keyword(null,"on-jsload","on-jsload",-395756602),cljs.core.identity);
var js_files = cljs.core.filter.call(null,((function (map__5432,map__5432__$1,on_jsload){
return (function (p1__5428_SHARP_){
return adzerk.boot_reload.reload.ends_with_QMARK_.call(null,p1__5428_SHARP_,".js");
});})(map__5432,map__5432__$1,on_jsload))
,changed);
if(cljs.core.seq.call(null,js_files)){
goog.async.DeferredList.gatherResults(cljs.core.clj__GT_js.call(null,cljs.core.map.call(null,((function (js_files,map__5432,map__5432__$1,on_jsload){
return (function (p1__5429_SHARP_){
return goog.net.jsloader.load(goog.Uri.parse(p1__5429_SHARP_).makeUnique());
});})(js_files,map__5432,map__5432__$1,on_jsload))
,js_files))).addCallbacks(((function (js_files,map__5432,map__5432__$1,on_jsload){
return (function() { 
var G__5433__delegate = function (_){
return on_jsload.call(null);
};
var G__5433 = function (var_args){
var _ = null;
if (arguments.length > 0) {
var G__5434__i = 0, G__5434__a = new Array(arguments.length -  0);
while (G__5434__i < G__5434__a.length) {G__5434__a[G__5434__i] = arguments[G__5434__i + 0]; ++G__5434__i;}
  _ = new cljs.core.IndexedSeq(G__5434__a,0);
} 
return G__5433__delegate.call(this,_);};
G__5433.cljs$lang$maxFixedArity = 0;
G__5433.cljs$lang$applyTo = (function (arglist__5435){
var _ = cljs.core.seq(arglist__5435);
return G__5433__delegate(_);
});
G__5433.cljs$core$IFn$_invoke$arity$variadic = G__5433__delegate;
return G__5433;
})()
;})(js_files,map__5432,map__5432__$1,on_jsload))
,((function (js_files,map__5432,map__5432__$1,on_jsload){
return (function (e){
return console.error("Load failed:",e.message);
});})(js_files,map__5432,map__5432__$1,on_jsload))
);

if(cljs.core.truth_((window["jQuery"]))){
return jQuery(document).trigger("page-load");
} else {
return null;
}
} else {
return null;
}
});
adzerk.boot_reload.reload.reload_html = (function reload_html(changed){
var page_path = adzerk.boot_reload.reload.page_uri.getPath();
var html_path = ((adzerk.boot_reload.reload.ends_with_QMARK_.call(null,page_path,"/"))?[cljs.core.str(page_path),cljs.core.str("index.html")].join(''):page_path);
if(cljs.core.truth_(adzerk.boot_reload.reload.changed_href_QMARK_.call(null,html_path,changed))){
return adzerk.boot_reload.reload.reload_page_BANG_.call(null);
} else {
return null;
}
});
adzerk.boot_reload.reload.group_log = (function group_log(title,things_to_log){
console.groupCollapsed(title);

var seq__5440_5444 = cljs.core.seq.call(null,things_to_log);
var chunk__5441_5445 = null;
var count__5442_5446 = (0);
var i__5443_5447 = (0);
while(true){
if((i__5443_5447 < count__5442_5446)){
var t_5448 = cljs.core._nth.call(null,chunk__5441_5445,i__5443_5447);
console.log(t_5448);

var G__5449 = seq__5440_5444;
var G__5450 = chunk__5441_5445;
var G__5451 = count__5442_5446;
var G__5452 = (i__5443_5447 + (1));
seq__5440_5444 = G__5449;
chunk__5441_5445 = G__5450;
count__5442_5446 = G__5451;
i__5443_5447 = G__5452;
continue;
} else {
var temp__4126__auto___5453 = cljs.core.seq.call(null,seq__5440_5444);
if(temp__4126__auto___5453){
var seq__5440_5454__$1 = temp__4126__auto___5453;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5440_5454__$1)){
var c__4369__auto___5455 = cljs.core.chunk_first.call(null,seq__5440_5454__$1);
var G__5456 = cljs.core.chunk_rest.call(null,seq__5440_5454__$1);
var G__5457 = c__4369__auto___5455;
var G__5458 = cljs.core.count.call(null,c__4369__auto___5455);
var G__5459 = (0);
seq__5440_5444 = G__5456;
chunk__5441_5445 = G__5457;
count__5442_5446 = G__5458;
i__5443_5447 = G__5459;
continue;
} else {
var t_5460 = cljs.core.first.call(null,seq__5440_5454__$1);
console.log(t_5460);

var G__5461 = cljs.core.next.call(null,seq__5440_5454__$1);
var G__5462 = null;
var G__5463 = (0);
var G__5464 = (0);
seq__5440_5444 = G__5461;
chunk__5441_5445 = G__5462;
count__5442_5446 = G__5463;
i__5443_5447 = G__5464;
continue;
}
} else {
}
}
break;
}

return console.groupEnd();
});
adzerk.boot_reload.reload.reload = (function reload(opts,changed){
adzerk.boot_reload.reload.group_log.call(null,"Reload",changed);

var G__5466 = changed;
adzerk.boot_reload.reload.reload_js.call(null,G__5466,opts);

adzerk.boot_reload.reload.reload_html.call(null,G__5466);

adzerk.boot_reload.reload.reload_css.call(null,G__5466);

adzerk.boot_reload.reload.reload_img.call(null,G__5466);

return G__5466;
});
