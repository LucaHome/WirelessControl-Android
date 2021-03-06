import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:redux/redux.dart';
import 'package:wireless_control/actions/wireless_socket.actions.dart';
import 'package:wireless_control/constants/color.constants.dart';
import 'package:wireless_control/enums/app_theme.enum.dart';
import 'package:wireless_control/helper/icon.helper.dart';
import 'package:wireless_control/middleware/wireless_socket.thunk_action.dart';
import 'package:wireless_control/models/app_state.model.dart';
import 'package:wireless_control/models/wireless_socket.model.dart';
import 'package:wireless_control/presentation/shared-presentation.dart';
import 'package:wireless_control/utils/actions.util.dart';

class WirelessSocketCard extends StatefulWidget {
  final WirelessSocket wirelessSocket;
  final Store<AppState> store;

  WirelessSocketCard(this.wirelessSocket, this.store);

  @override
  WirelessSocketCardState createState() {
    return new WirelessSocketCardState();
  }
}

class WirelessSocketCardState extends State<WirelessSocketCard> {

  Widget wirelessSocketCard(BuildContext context, Size pageSize) {
    return new Positioned(
      right: 0.0,
      child: new Container(
        width: pageSize.width * 0.65,
        height: 140.0,
        child: new Card(
          color: widget.store.state.theme == AppTheme.Light ? ColorConstants.CardBackgroundLightTransparent : ColorConstants.CardBackgroundDarkTransparent,
          child: InkWell(
              splashColor: ColorConstants.ButtonSubmit,
              onTap: () {
                widget.store.dispatch(new WirelessSocketSelectSuccessful(wirelessSocket: widget.wirelessSocket));
                Navigator.pushNamed(context, '/details-wireless-socket');
              },
              child: new Padding(
                padding: const EdgeInsets.only(
                  top: 8.0,
                  bottom: 8.0,
                  left: 24.0,
                  right: 0.0,
                ),
                child: new  BackdropFilter(
                  filter: new ImageFilter.blur(sigmaX: 8.0, sigmaY: 8.0),
                  child: new Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: <Widget>[
                      new Text(widget.wirelessSocket.name, style: TextStyle(color: widget.store.state.theme == AppTheme.Light ? ColorConstants.TextDark : ColorConstants.TextLight, fontSize: 18)),
                      new Text(widget.wirelessSocket.code, style: TextStyle(color: widget.store.state.theme == AppTheme.Light ? ColorConstants.TextDark : ColorConstants.TextLight, fontSize: 15)),
                      new Text(widget.wirelessSocket.area, style: TextStyle(color: widget.store.state.theme == AppTheme.Light ? ColorConstants.TextDark : ColorConstants.TextLight, fontSize: 12)),
                      new Text(widget.wirelessSocket.description, style: TextStyle(color: widget.store.state.theme == AppTheme.Light ? ColorConstants.TextDark : ColorConstants.TextLight, fontSize: 12)),
                      new Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: <Widget>[
                          new IconButton(
                              splashColor: ColorConstants.ButtonSubmit,
                              icon: new Icon(
                                Icons.add_alarm,
                                size: 22,
                                color: widget.store.state.theme == AppTheme.Light ? ColorConstants.IconDark : ColorConstants.IconLight,
                              ),
                              onPressed: () {
                                widget.store.dispatch(new WirelessSocketSelectSuccessful(wirelessSocket: widget.wirelessSocket));
                                Navigator.pushNamed(context, '/list-periodic-task');
                              })
                        ],
                      ),
                    ],
                  ),
                ),
              ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    var pageSize = MediaQuery.of(context).size;

    return new Card(
      color: widget.store.state.theme == AppTheme.Light ? ColorConstants.CardBackgroundLight : ColorConstants.CardBackgroundDark,
      margin: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 8.0),
      child: new Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 8.0),
        child: new Container(
          height: 115.0,
          child: new Stack(
            children: <Widget>[
              widget.wirelessSocket.state == 1 ? waveWidgetOn() : waveWidgetOff(),
              wirelessSocketCard(context, pageSize),
              new Positioned(
                  top: 5,
                  left: 5,
                  bottom: 5,
                  child: FlatButton(
                    color: Color.fromARGB(0, 0, 0, 0),
                    splashColor: ColorConstants.ButtonSubmit,
                    highlightColor: ColorConstants.ButtonSubmitHighlight,
                    padding: const EdgeInsets.all(10.0),
                    onPressed: () {
                      widget.wirelessSocket.state = widget.wirelessSocket.state == 1 ? 0 : 1;
                      widget.store.dispatch(updateWirelessSocket(
                          widget.store.state.nextCloudCredentials,
                          widget.wirelessSocket,
                          () => onSuccess(context, 'Successfully set state for ${widget.wirelessSocket.name}'),
                          () => onError(context, 'Failed to set state for ${widget.wirelessSocket.name}')));
                    },
                    child: new Icon(
                      fromString(widget.wirelessSocket.icon),
                      size: 50,
                      color: widget.store.state.theme == AppTheme.Light ? ColorConstants.IconDark : ColorConstants.IconLight,
                    ),
                  ))],
          ),
        ),
      ),
    );
  }
}
