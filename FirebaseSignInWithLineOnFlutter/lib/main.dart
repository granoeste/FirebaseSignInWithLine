import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_line_login/flutter_line_login.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

import 'const.dart';

final FirebaseAuth _auth = FirebaseAuth.instance;
final FlutterLineLogin _flutterLineLogin = new FlutterLineLogin();

void main() => runApp(new MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Flutter Firebase Signin with LINE',
      theme: new ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: new MyHomePage(title: 'Flutter Firebase Signin with LINE'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _MyHomePageState createState() => new _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _message = '';

  set message(String value) {
    setState(() {
      _message = value;
    });
  }

  Future _onLoginSuccess(Object data) async {
    var result = data as Map;

    debugPrint("Line Login Success");
    debugPrint("  userID:${result['userID']}");
    debugPrint("  displayName:${result['displayName']}");
    debugPrint("  pictureUrl:${result['pictureUrl']}");
    debugPrint("  statusMessage:${result['statusMessage']}");
    debugPrint("  accessToken: ${result['accessToken']}.");
    debugPrint("  expiresIn: ${result['expiresIn']}.");

    await http
        .post(urlVerifyToken,
            body: json.encode({"accessToken": "${result['accessToken']}"}),
            headers: {'content-type': 'application/json'})
        .then((response) {
          debugPrint("Response status: ${response.statusCode}");
          debugPrint("Response body: ${response.body}");
          if (response.statusCode >= 400) {
            return Future.error({
              "code": "ERROR",
              "status": response.statusCode,
              "message": "Http error.",
              "detail": response.body
            });
          }

          Map<String, dynamic> res = json.decode(response.body);
          var firebaseToken = res['firebase_token'];

          if (firebaseToken == null || firebaseToken == "") {
            return Future.error({
              "code": "ERROR",
              "status": response.statusCode,
              "message": "firebaseToken is empty.",
              "detail": response.body
            });
          }

          return Future<String>.value(firebaseToken);
        })
        .then((token) => _auth.signInWithCustomToken(token: token))
        .then((firebaseUser) {
          message = '${firebaseUser}';

          debugPrint("Firebase Sign-in Success");
          debugPrint("  uid: ${firebaseUser.uid}");
          debugPrint("  displayName: ${firebaseUser.displayName}");
          debugPrint("  photoUrl: ${firebaseUser.photoUrl}");
        })
        .catchError((error) => debugPrint("ERROR:${error}"))
        .whenComplete(() => debugPrint("done."));
  }

  void _onLoginError(Object error) {
    debugPrint("LoginError: ${error}");
  }

  Future<Null> _startLogin() async {
    await _flutterLineLogin.startLogin(_onLoginSuccess, _onLoginError);
  }

  Future<Null> _logout() async {
    try {
      await _flutterLineLogin.logout();
      debugPrint("Logout");
      await _auth.signOut();
    } on PlatformException catch (e) {
      debugPrint("LoginError: ${e}");
    }
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
      appBar: new AppBar(
        title: new Text(widget.title),
      ),
      body: Padding(
        padding: new EdgeInsets.all(8.0),
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              RaisedButton.icon(
                label: Text('Log in with LINE'),
                icon: ImageIcon(new AssetImage("images/line_120.png")),
                color: const Color(0xFF00C300),
                textColor: const Color(0xFFFFFFFF),
                disabledColor: const Color(0xFFC6C6C6),
                onPressed: () => _startLogin(),
              ),
              SizedBox(height: 8.0),
              RaisedButton(
                  child: Text('Log out'),
                  color: const Color(0xFF00C300),
                  textColor: const Color(0xFFFFFFFF),
                  disabledColor: const Color(0xFFC6C6C6),
                  onPressed: () => _logout()),
              SizedBox(height: 16.0),
              Expanded(
                flex: 1,
                child: SingleChildScrollView(
                  child: Text(_message,
                      style: TextStyle(color: Color.fromARGB(255, 0, 155, 0))),
                ),
              )
            ]),
      ),
    );
  }
}
