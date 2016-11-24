var MSD = (function($) {
	
	var init = function() {
		
	};
	
	var performAction = function(method) {
		var body = {};
		body.method = method;
		$.post('./performAction', JSON.stringify(body), function() {
			console.log('Requested machine state change: ' + method);
		}, 'json');
	};
	
	var performActionWithParam = function(method, param) {
		var body = {};
		body.method = method;
		body.param = param;
		$.post('./performActionWithParam', JSON.stringify(body), function() {
			console.log('Requested machine state change with param: ' + method + ' ' + param);
		}, 'json');
	};
	
	return {
		init : init,
		performAction : performAction,
		performActionWithParam: performActionWithParam
	} 
})($);