import 'package:flutter/material.dart';
import 'package:flutter_redux/flutter_redux.dart';
import 'package:lucahome_flutter/middleware/next_cloud_credentials.thunk_action.dart';
import 'package:lucahome_flutter/models/app_state.model.dart';
import 'package:lucahome_flutter/models/next_cloud_credentials.model.dart';
import 'package:lucahome_flutter/reducers/app.reducer.dart';
import 'package:lucahome_flutter/routes.dart';
import 'package:lucahome_flutter/utils/shared_pref.utils.dart';
import 'package:redux/redux.dart';
import 'package:redux_thunk/redux_thunk.dart';

void main() => runApp(new MainApp());

class MainApp extends StatelessWidget {
  final String title = 'LucaHome';

  MainApp();

  @override
  Widget build(BuildContext context) {
    var store = new Store<AppState>(appReducer,
        initialState: new AppState(),
        distinct: true,
        middleware: [thunkMiddleware]);

    return FutureBuilder(
        future: loadNextCloudCredentials(),
        builder: (BuildContext context,
            AsyncSnapshot<NextCloudCredentials> snapshot) {
          switch (snapshot.connectionState) {
            case ConnectionState.none:
            case ConnectionState.waiting:
            case ConnectionState.active:
              return new Center(child: new CircularProgressIndicator());
            case ConnectionState.done:
              var nextCloudCredentials = snapshot.data;
              if (nextCloudCredentials.hasServer() &&
                  nextCloudCredentials.isLoggedIn()) {
                store.dispatch(logIn(nextCloudCredentials));

                return new StoreProvider(
                  store: store,
                  child: new MaterialApp(
                    debugShowCheckedModeBanner: false,
                    title: title,
                    routes: getRoutes(context, store),
                    initialRoute: '/loading',
                  ),
                );
              } else {
                return new StoreProvider(
                  store: store,
                  child: new MaterialApp(
                    debugShowCheckedModeBanner: false,
                    title: title,
                    routes: getRoutes(context, store),
                    initialRoute: '/login',
                  ),
                );
              }
          }
        });
  }
}